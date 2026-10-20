package io.railflow.inventory.core;

import java.io.Serializable;
import java.time.Instant;
import java.util.*;

/**
 * Single-writer, deterministic, event-sourced actor state machine.
 * Owns ONE (train_number, journey_date) partition.
 * PURE logic: No I/O, no wall clock, no random generator.
 * Same log offset prefix => byte-identical state.
 */
public final class InventoryPartition implements Serializable {

    private final String trainNumber;
    private final String journeyDate;
    private final int totalSegments;
    private final Map<String, QuotaPool> pools; // Key: classCode:quotaCode
    private final Map<UUID, ActiveHold> activeHolds;
    private final Map<UUID, ConfirmedBookingRecord> confirmedBookings;
    private final AllocationScorer scorer;
    private long appliedOffset;

    public record ActiveHold(
        UUID bookingId,
        String classCode,
        String quotaCode,
        int fromIdx,
        int toIdx,
        List<HeldBerthAssignment> assignments,
        Instant expiresAt
    ) implements Serializable {}

    public record HeldBerthAssignment(
        UUID passengerId,
        int berthIdx,
        BerthMeta meta,
        long segmentMask,
        String status, // CNF, RAC, WL
        int positionNumber
    ) implements Serializable {}

    public record ConfirmedBookingRecord(
        UUID bookingId,
        String classCode,
        String quotaCode,
        int fromIdx,
        int toIdx,
        List<HeldBerthAssignment> assignments,
        Instant confirmedAt
    ) implements Serializable {}

    public InventoryPartition(String trainNumber, String journeyDate, int totalSegments) {
        this.trainNumber = trainNumber;
        this.journeyDate = journeyDate;
        this.totalSegments = totalSegments;
        this.pools = new LinkedHashMap<>();
        this.activeHolds = new LinkedHashMap<>();
        this.confirmedBookings = new LinkedHashMap<>();
        this.scorer = new AllocationScorer();
        this.appliedOffset = -1L;
    }

    public void registerQuotaPool(QuotaPool pool) {
        pools.put(pool.classCode() + ":" + pool.quotaCode(), pool);
    }

    public String trainNumber() { return trainNumber; }
    public String journeyDate() { return journeyDate; }
    public long appliedOffset() { return appliedOffset; }
    public int totalSegments() { return totalSegments; }
    public Map<String, QuotaPool> pools() { return Collections.unmodifiableMap(pools); }
    public int activeHoldsCount() { return activeHolds.size(); }
    public int confirmedBookingsCount() { return confirmedBookings.size(); }

    /**
     * Authoritative PURE state mutation function.
     */
    public InventoryEvent apply(InventoryCommand cmd) {
        this.appliedOffset = cmd.offset();

        return switch (cmd) {
            case InventoryCommand.HoldBerthsCommand hold -> handleHold(hold);
            case InventoryCommand.ConfirmBerthsCommand confirm -> handleConfirm(confirm);
            case InventoryCommand.ReleaseBerthsCommand release -> handleRelease(release);
            case InventoryCommand.CancelBerthsCommand cancel -> handleCancel(cancel);
            case InventoryCommand.PrepareChartCommand chart -> handleChart(chart);
        };
    }

    private InventoryEvent handleHold(InventoryCommand.HoldBerthsCommand cmd) {
        String poolKey = cmd.classCode() + ":" + cmd.quotaCode();
        QuotaPool pool = pools.get(poolKey);
        if (pool == null) {
            // Fallback to General (GN) if specific quota pool is absent
            pool = pools.get(cmd.classCode() + ":GN");
        }
        if (pool == null) {
            return new InventoryEvent.NoAccommodationEvent(
                cmd.offset(), cmd.issuedAt(), trainNumber, journeyDate, cmd.bookingId(), "Quota pool not provisioned: " + poolKey
            );
        }

        SegmentMask reqMask = SegmentMask.of(cmd.fromIdx(), cmd.toIdx());
        List<HeldBerthAssignment> assignments = new ArrayList<>();
        List<BerthMeta> partyAllocated = new ArrayList<>();
        List<InventoryEvent.HeldPassengerAllocation> eventAllocations = new ArrayList<>();

        for (PassengerRequest pax : cmd.passengers()) {
            int allocatedIdx = pool.engine().allocateBestFit(reqMask, pax, partyAllocated, scorer);
            if (allocatedIdx != -1) {
                BerthMeta meta = pool.engine().getMeta(allocatedIdx);
                partyAllocated.add(meta);
                HeldBerthAssignment assignment = new HeldBerthAssignment(
                    pax.passengerId(), allocatedIdx, meta, reqMask.asSingleWord(), "CNF", 0
                );
                assignments.add(assignment);
                eventAllocations.add(new InventoryEvent.HeldPassengerAllocation(
                    pax.passengerId(), meta.coachId(), meta.berthNumber(), meta.berthType(), meta.bayNumber(), reqMask.asSingleWord(), "CNF"
                ));
            } else if (pool.hasRacCapacity()) {
                int racPos = pool.incrementRac();
                HeldBerthAssignment assignment = new HeldBerthAssignment(
                    pax.passengerId(), -1, null, reqMask.asSingleWord(), "RAC", racPos
                );
                assignments.add(assignment);
                eventAllocations.add(new InventoryEvent.HeldPassengerAllocation(
                    pax.passengerId(), "RAC", racPos, BerthType.SIDE_LOWER, 0, reqMask.asSingleWord(), "RAC"
                ));
            } else if (pool.hasWaitlistCapacity()) {
                int wlPos = pool.incrementWaitlist();
                String wlType = determineWaitlistType(cmd.quotaCode(), cmd.fromIdx(), cmd.toIdx());
                HeldBerthAssignment assignment = new HeldBerthAssignment(
                    pax.passengerId(), -1, null, reqMask.asSingleWord(), wlType, wlPos
                );
                assignments.add(assignment);
                eventAllocations.add(new InventoryEvent.HeldPassengerAllocation(
                    pax.passengerId(), wlType, wlPos, BerthType.LOWER, 0, reqMask.asSingleWord(), wlType
                ));
            } else {
                // Roll back any partially allocated berths for this party
                for (HeldBerthAssignment prev : assignments) {
                    if (prev.berthIdx() >= 0) {
                        pool.engine().release(prev.berthIdx(), reqMask);
                    } else if ("RAC".equals(prev.status())) {
                        pool.decrementRac();
                    } else {
                        pool.decrementWaitlist();
                    }
                }
                return new InventoryEvent.NoAccommodationEvent(
                    cmd.offset(), cmd.issuedAt(), trainNumber, journeyDate, cmd.bookingId(), "REGRET: Waitlist exhausted"
                );
            }
        }

        Instant expiresAt = cmd.issuedAt().plusSeconds(cmd.holdTtlSeconds());
        ActiveHold hold = new ActiveHold(
            cmd.bookingId(), cmd.classCode(), cmd.quotaCode(), cmd.fromIdx(), cmd.toIdx(), assignments, expiresAt
        );
        activeHolds.put(cmd.bookingId(), hold);

        return new InventoryEvent.BerthHeldEvent(
            cmd.offset(), cmd.issuedAt(), trainNumber, journeyDate, cmd.bookingId(),
            cmd.classCode(), cmd.quotaCode(), cmd.fromIdx(), cmd.toIdx(), eventAllocations, expiresAt
        );
    }

    private InventoryEvent handleConfirm(InventoryCommand.ConfirmBerthsCommand cmd) {
        ActiveHold hold = activeHolds.remove(cmd.bookingId());
        if (hold != null) {
            confirmedBookings.put(cmd.bookingId(), new ConfirmedBookingRecord(
                hold.bookingId(), hold.classCode(), hold.quotaCode(), hold.fromIdx(), hold.toIdx(), hold.assignments(), cmd.issuedAt()
            ));
            return new InventoryEvent.BerthConfirmedEvent(cmd.offset(), cmd.issuedAt(), trainNumber, journeyDate, cmd.bookingId());
        }
        return new InventoryEvent.BerthConfirmedEvent(cmd.offset(), cmd.issuedAt(), trainNumber, journeyDate, cmd.bookingId());
    }

    private InventoryEvent handleRelease(InventoryCommand.ReleaseBerthsCommand cmd) {
        ActiveHold hold = activeHolds.remove(cmd.bookingId());
        if (hold != null) {
            String poolKey = hold.classCode() + ":" + hold.quotaCode();
            QuotaPool pool = pools.get(poolKey);
            if (pool != null) {
                SegmentMask mask = SegmentMask.of(hold.fromIdx(), hold.toIdx());
                for (HeldBerthAssignment a : hold.assignments()) {
                    if (a.berthIdx() >= 0) {
                        pool.engine().release(a.berthIdx(), mask);
                    } else if ("RAC".equals(a.status())) {
                        pool.decrementRac();
                    } else {
                        pool.decrementWaitlist();
                    }
                }
            }
        }
        return new InventoryEvent.BerthReleasedEvent(cmd.offset(), cmd.issuedAt(), trainNumber, journeyDate, cmd.bookingId(), cmd.reason());
    }

    private InventoryEvent handleCancel(InventoryCommand.CancelBerthsCommand cmd) {
        ConfirmedBookingRecord booking = confirmedBookings.remove(cmd.bookingId());
        if (booking != null) {
            String poolKey = booking.classCode() + ":" + booking.quotaCode();
            QuotaPool pool = pools.get(poolKey);
            if (pool != null) {
                SegmentMask mask = SegmentMask.of(booking.fromIdx(), booking.toIdx());
                for (HeldBerthAssignment a : booking.assignments()) {
                    if (a.berthIdx() >= 0) {
                        pool.engine().release(a.berthIdx(), mask);
                    }
                }
            }
        }
        return new InventoryEvent.BerthReleasedEvent(cmd.offset(), cmd.issuedAt(), trainNumber, journeyDate, cmd.bookingId(), "CANCELLED_BY_USER");
    }

    private InventoryEvent handleChart(InventoryCommand.PrepareChartCommand cmd) {
        // Run RAC -> Confirmed and Waitlist -> RAC promotions
        int promotedRac = 0;
        int promotedWl = 0;
        int cancelledWl = 0;
        int releasedBerths = 0;

        for (QuotaPool pool : pools.values()) {
            if ("FIRST_CHART_T4".equals(cmd.chartMilestone())) {
                // Promote RAC if berths opened up
                SegmentMask fullRoute = SegmentMask.of(0, totalSegments);
                while (pool.engine().canFitAny(fullRoute) && pool.currentRacCount() > 0) {
                    PassengerRequest mockPax = PassengerRequest.of("PromotedRAC", 30, "M", BerthType.LOWER);
                    int allocated = pool.engine().allocateBestFit(fullRoute, mockPax, null, scorer);
                    if (allocated >= 0) {
                        pool.decrementRac();
                        promotedRac++;
                    } else {
                        break;
                    }
                }
            }
        }

        return new InventoryEvent.ChartPreparedEvent(
            cmd.offset(), cmd.issuedAt(), trainNumber, journeyDate, cmd.chartMilestone(),
            promotedRac, promotedWl, cancelledWl, releasedBerths
        );
    }

    private String determineWaitlistType(String quotaCode, int fromIdx, int toIdx) {
        if ("TQ".equals(quotaCode)) return "TQWL";
        if (fromIdx == 0) return "GNWL";
        if (toIdx < totalSegments) return "RLWL";
        return "PQWL";
    }

    /**
     * Compute exact physical occupancy bitmask popcount across all berths.
     * Used for mathematical invariant verification.
     */
    public long computeTotalSoldSegments() {
        long total = 0;
        for (QuotaPool pool : pools.values()) {
            for (int b = 0; b < pool.engine().totalBerths(); b++) {
                total += Long.bitCount(pool.engine().getOccupancy(b));
            }
        }
        return total;
    }
}
