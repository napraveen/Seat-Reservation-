package io.railflow.inventory.core;

import net.jqwik.api.*;
import org.junit.jupiter.api.Assertions;

import java.time.Instant;
import java.util.*;

public class AllocationPropertyTest {

    @Property(tries = 1000)
    void invariantIHoldsUnderRandomBookingStreams(
        @ForAll("randomBookingStreams") List<BookingOp> operations
    ) {
        int totalSegments = 10;
        int totalBerths = 12;

        BerthMeta[] metadata = new BerthMeta[totalBerths];
        for (int i = 0; i < totalBerths; i++) {
            metadata[i] = BerthMeta.of(i, "B1", i + 1, BerthType.LOWER, i / 8);
        }

        InventoryEngine engine = new BerthMajorInventoryEngine(totalSegments, metadata);
        QuotaPool pool = new QuotaPool("3A", "GN", engine, 10, 20);
        InventoryPartition partition = new InventoryPartition("12951", "2026-10-22", totalSegments);
        partition.registerQuotaPool(pool);

        long offset = 0;
        Map<UUID, UUID> activeBookingIds = new HashMap<>();

        for (BookingOp op : operations) {
            offset++;
            Instant now = Instant.ofEpochMilli(1761000000000L + offset * 100);

            if (op.isCancel() && !activeBookingIds.isEmpty()) {
                UUID toCancel = activeBookingIds.keySet().iterator().next();
                activeBookingIds.remove(toCancel);
                partition.apply(new InventoryCommand.CancelBerthsCommand(
                    offset, now, "12951", "2026-10-22", toCancel, List.of()
                ));
            } else {
                UUID bId = UUID.randomUUID();
                PassengerRequest pax = PassengerRequest.of("Pax-" + offset, 30, "M", BerthType.LOWER);
                InventoryEvent event = partition.apply(new InventoryCommand.HoldBerthsCommand(
                    offset, now, "12951", "2026-10-22", bId, "3A", "GN", op.from(), op.to(), List.of(pax), 600
                ));

                if (event instanceof InventoryEvent.BerthHeldEvent) {
                    offset++;
                    partition.apply(new InventoryCommand.ConfirmBerthsCommand(
                        offset, now.plusSeconds(5), "12951", "2026-10-22", bId
                    ));
                    activeBookingIds.put(bId, bId);
                }
            }

            // Invariant I Assertion:
            // For every berth, segment masks of allocated bookings must be pairwise disjoint
            // And sum(popcount(berthMask)) must equal total active segments
            long calculatedSoldSegments = 0;
            for (int b = 0; b < totalBerths; b++) {
                long occupancy = engine.getOccupancy(b);
                calculatedSoldSegments += Long.bitCount(occupancy);
            }
            Assertions.assertEquals(calculatedSoldSegments, partition.computeTotalSoldSegments());
        }
    }

    record BookingOp(int from, int to, boolean isCancel) {}

    @Provide
    Arbitrary<List<BookingOp>> randomBookingStreams() {
        Arbitrary<BookingOp> opArbitrary = Combinators.combine(
            Arbitraries.integers().between(0, 8),
            Arbitraries.integers().between(1, 9),
            Arbitraries.integers().between(1, 10)
        ).as((start, span, cancelRoll) -> {
            int to = Math.min(10, start + span);
            if (to <= start) to = start + 1;
            boolean cancel = (cancelRoll <= 2);
            return new BookingOp(start, to, cancel);
        });

        return opArbitrary.list().ofMinSize(5).ofMaxSize(30);
    }
}
