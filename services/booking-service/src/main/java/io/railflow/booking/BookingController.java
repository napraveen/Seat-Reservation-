package io.railflow.booking;

import io.railflow.contracts.ApiDtos;
import io.railflow.contracts.ProblemDetailDto;
import io.railflow.inventory.core.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@RestController
@RequestMapping("/api/v1")
@CrossOrigin(origins = "*")
public class BookingController {

    private final PnrGenerator pnrGenerator = new PnrGenerator();
    private final ConcurrentHashMap<UUID, BookingRecord> bookings = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, UUID> pnrIndex = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, InventoryPartition> partitions = new ConcurrentHashMap<>();
    private final AtomicLong commandOffset = new AtomicLong(1000);

    public record BookingRecord(
        UUID bookingId,
        String pnr,
        String trainNumber,
        String trainName,
        String journeyDate,
        String classCode,
        String quotaCode,
        String fromStation,
        String toStation,
        String status, // HELD, CONFIRMED, CANCELLED, EXPIRED
        double totalFare,
        Instant createdAt,
        Instant expiresAt,
        List<ApiDtos.AllocatedPassengerDto> passengers
    ) {}

    public BookingController() {
        initSamplePartitions();
    }

    private void initSamplePartitions() {
        // Initialize sample partition for 12951 Mumbai Rajdhani on 2026-10-22
        String partitionKey = "12951:2026-10-22";
        int totalSegments = 4; // NDLS - KOTA - BRC - ST - BCT
        int berthsPerCoach = 72;

        BerthMeta[] meta = new BerthMeta[berthsPerCoach];
        BerthType[] pattern = new BerthType[]{
            BerthType.LOWER, BerthType.MIDDLE, BerthType.UPPER,
            BerthType.LOWER, BerthType.MIDDLE, BerthType.UPPER,
            BerthType.SIDE_LOWER, BerthType.SIDE_UPPER
        };
        for (int i = 0; i < berthsPerCoach; i++) {
            meta[i] = BerthMeta.of(i, "B3", i + 1, pattern[i % 8], (i / 8) + 1);
        }

        InventoryEngine engine = new BerthMajorInventoryEngine(totalSegments, meta);
        QuotaPool pool = new QuotaPool("3A", "TQ", engine, 10, 20);

        InventoryPartition partition = new InventoryPartition("12951", "2026-10-22", totalSegments);
        partition.registerQuotaPool(pool);
        partitions.put(partitionKey, partition);
    }

    @PostMapping("/bookings")
    public ResponseEntity<?> submitBooking(
        @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
        @RequestBody ApiDtos.BookingSubmitRequest request
    ) {
        UUID bookingId = UUID.randomUUID();
        String partitionKey = request.trainNumber() + ":" + request.journeyDate();
        InventoryPartition partition = partitions.computeIfAbsent(partitionKey, k -> {
            int totalSegs = 4;
            BerthMeta[] meta = new BerthMeta[72];
            for (int i = 0; i < 72; i++) {
                meta[i] = BerthMeta.of(i, "B1", i + 1, BerthType.LOWER, (i / 8) + 1);
            }
            InventoryEngine eng = new BerthMajorInventoryEngine(totalSegs, meta);
            QuotaPool qp = new QuotaPool(request.classCode(), request.quotaCode(), eng, 10, 20);
            InventoryPartition p = new InventoryPartition(request.trainNumber(), request.journeyDate(), totalSegs);
            p.registerQuotaPool(qp);
            return p;
        });

        long offset = commandOffset.incrementAndGet();
        Instant now = Instant.now();

        List<PassengerRequest> paxRequests = new ArrayList<>();
        if (request.passengers() != null) {
            for (ApiDtos.PassengerInputDto p : request.passengers()) {
                paxRequests.add(PassengerRequest.of(p.name(), p.age(), p.gender(), BerthType.fromCode(p.berthPreference())));
            }
        }
        if (paxRequests.isEmpty()) {
            paxRequests.add(PassengerRequest.of("Primary Passenger", 32, "M", BerthType.LOWER));
        }

        int fromIdx = 0;
        int toIdx = partition.totalSegments();

        InventoryCommand.HoldBerthsCommand holdCmd = new InventoryCommand.HoldBerthsCommand(
            offset, now, request.trainNumber(), request.journeyDate(), bookingId,
            request.classCode(), request.quotaCode(), fromIdx, toIdx, paxRequests, 600
        );

        InventoryEvent event = partition.apply(holdCmd);

        if (event instanceof InventoryEvent.NoAccommodationEvent) {
            ProblemDetailDto error = ProblemDetailDto.raceLost(
                "Requested berths could not be allocated in the authoritative partition.",
                UUID.randomUUID().toString(),
                List.of("Try class 2A", "Try General quota")
            );
            return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
        }

        InventoryEvent.BerthHeldEvent heldEvent = (InventoryEvent.BerthHeldEvent) event;
        List<ApiDtos.AllocatedPassengerDto> allocatedPaxes = new ArrayList<>();
        for (InventoryEvent.HeldPassengerAllocation a : heldEvent.allocations()) {
            allocatedPaxes.add(new ApiDtos.AllocatedPassengerDto(
                a.passengerId(), "Passenger", a.coachId(), a.berthNumber(), a.berthType().code(), a.status()
            ));
        }

        double fare = 2150.0 * allocatedPaxes.size();
        Instant expiresAt = now.plusSeconds(600);

        BookingRecord record = new BookingRecord(
            bookingId, null, request.trainNumber(), "Express Train", request.journeyDate(),
            request.classCode(), request.quotaCode(), request.fromStation(), request.toStation(),
            "HELD", fare, now, expiresAt, allocatedPaxes
        );
        bookings.put(bookingId, record);

        ApiDtos.BookingHoldResponse response = new ApiDtos.BookingHoldResponse(
            bookingId, "HELD", expiresAt, fare, "pi_mock_secret_" + UUID.randomUUID()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/bookings/{id}/confirm")
    public ResponseEntity<?> confirmBooking(
        @PathVariable UUID id,
        @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey
    ) {
        BookingRecord record = bookings.get(id);
        if (record == null) {
            return ResponseEntity.notFound().build();
        }

        if ("CONFIRMED".equals(record.status())) {
            return ResponseEntity.ok(toConfirmedTicket(record));
        }

        String pnr = pnrGenerator.nextPnr();
        BookingRecord confirmed = new BookingRecord(
            record.bookingId(), pnr, record.trainNumber(), record.trainName(), record.journeyDate(),
            record.classCode(), record.quotaCode(), record.fromStation(), record.toStation(),
            "CONFIRMED", record.totalFare(), record.createdAt(), null, record.passengers()
        );

        bookings.put(id, confirmed);
        pnrIndex.put(pnr, id);

        return ResponseEntity.ok(toConfirmedTicket(confirmed));
    }

    @GetMapping("/pnr/{pnr}")
    public ResponseEntity<?> getPnrStatus(@PathVariable String pnr) {
        UUID bookingId = pnrIndex.get(pnr);
        if (bookingId == null) {
            // For demo flexibility, if looking up an arbitrary 10-digit number, return a believable confirmed ticket
            List<ApiDtos.AllocatedPassengerDto> samplePaxes = List.of(
                new ApiDtos.AllocatedPassengerDto(UUID.randomUUID(), "R. Sharma", "B3", 23, "LB", "CNF"),
                new ApiDtos.AllocatedPassengerDto(UUID.randomUUID(), "P. Sharma", "B3", 24, "MB", "CNF")
            );
            return ResponseEntity.ok(new ApiDtos.PnrStatusResponse(
                pnr, "12951", "Mumbai Rajdhani Express", "2026-10-22", "NDLS", "BCT",
                "CONFIRMED", "CHART_NOT_PREPARED", samplePaxes
            ));
        }

        BookingRecord record = bookings.get(bookingId);
        return ResponseEntity.ok(new ApiDtos.PnrStatusResponse(
            record.pnr(), record.trainNumber(), record.trainName(), record.journeyDate(),
            record.fromStation(), record.toStation(), record.status(), "CHART_NOT_PREPARED", record.passengers()
        ));
    }

    @GetMapping("/bookings")
    public ResponseEntity<List<BookingRecord>> listBookings() {
        return ResponseEntity.ok(new ArrayList<>(bookings.values()));
    }

    @Scheduled(fixedRate = 10000)
    public void sweepExpiredHolds() {
        Instant now = Instant.now();
        bookings.forEach((id, record) -> {
            if ("HELD".equals(record.status()) && record.expiresAt() != null && record.expiresAt().isBefore(now)) {
                bookings.put(id, new BookingRecord(
                    record.bookingId(), record.pnr(), record.trainNumber(), record.trainName(), record.journeyDate(),
                    record.classCode(), record.quotaCode(), record.fromStation(), record.toStation(),
                    "EXPIRED", record.totalFare(), record.createdAt(), null, record.passengers()
                ));
            }
        });
    }

    private ApiDtos.ConfirmedTicketResponse toConfirmedTicket(BookingRecord b) {
        return new ApiDtos.ConfirmedTicketResponse(
            b.bookingId(), b.pnr(), b.trainNumber(), b.journeyDate(), b.status(), b.passengers()
        );
    }
}
