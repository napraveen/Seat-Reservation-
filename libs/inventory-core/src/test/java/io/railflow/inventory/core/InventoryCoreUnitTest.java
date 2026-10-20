package io.railflow.inventory.core;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public class InventoryCoreUnitTest {

    @Test
    @DisplayName("SegmentMask: SingleWordMask calculates accurate segment spans and bit overlaps")
    void testSingleWordMaskBitwiseLogic() {
        SegmentMask seg0to2 = SegmentMask.of(0, 2); // bits 0 and 1: 0b0011 = 3
        SegmentMask seg2to4 = SegmentMask.of(2, 4); // bits 2 and 3: 0b1100 = 12
        SegmentMask seg1to3 = SegmentMask.of(1, 3); // bits 1 and 2: 0b0110 = 6

        Assertions.assertEquals(3L, seg0to2.asSingleWord());
        Assertions.assertEquals(12L, seg2to4.asSingleWord());
        Assertions.assertEquals(6L, seg1to3.asSingleWord());

        // seg0to2 and seg2to4 share no segment
        Assertions.assertFalse(seg0to2.overlaps(seg2to4));
        // seg1to3 overlaps both
        Assertions.assertTrue(seg0to2.overlaps(seg1to3));
        Assertions.assertTrue(seg2to4.overlaps(seg1to3));
    }

    @Test
    @DisplayName("The Segment Tree Trap: Proves Range-Min is only an upper bound and causes false availability")
    void testSegmentTreeBoundTrap() {
        int totalSegments = 2; // stops 0, 1, 2 => segments 0 and 1
        SegmentTreeEstimate tree = new SegmentTreeEstimate(totalSegments, 2); // 2 berths initially

        // Berth 1 booked for segment 0
        tree.updateSegmentDelta(0, -1);
        // Berth 2 booked for segment 1
        tree.updateSegmentDelta(1, -1);

        // Segment tree reports min(freeCount[0..1]) = min(1, 1) = 1
        int estimatedFree = tree.queryRangeMin(0, 1);
        Assertions.assertEquals(1, estimatedFree, "Segment tree falsely estimates 1 berth available for full journey");

        // Now test authoritative BerthMajorInventoryEngine with the exact same bookings:
        BerthMeta[] meta = new BerthMeta[]{
            BerthMeta.of(0, "S1", 1, BerthType.LOWER, 1),
            BerthMeta.of(1, "S1", 2, BerthType.MIDDLE, 1)
        };
        BerthMajorInventoryEngine engine = new BerthMajorInventoryEngine(totalSegments, meta);

        // Book Berth 0 for Segment 0 (0..1)
        boolean b0 = engine.allocateSpecific(0, SegmentMask.of(0, 1));
        // Book Berth 1 for Segment 1 (1..2)
        boolean b1 = engine.allocateSpecific(1, SegmentMask.of(1, 2));
        Assertions.assertTrue(b0 && b1);

        // Exact engine check for full route (0..2):
        int exactAvailable = engine.countAvailableBerths(SegmentMask.of(0, 2));
        Assertions.assertEquals(0, exactAvailable, "Exact engine authoritatively proves 0 berths can fit the full route!");
    }

    @Test
    @DisplayName("AllocationScorer: Favors Lower Berths for senior citizens and preserves virgin berths")
    void testScoringPrioritization() {
        AllocationScorer scorer = new AllocationScorer();
        BerthMeta lowerBerth = BerthMeta.of(0, "B1", 1, BerthType.LOWER, 1);
        BerthMeta upperBerth = BerthMeta.of(1, "B1", 3, BerthType.UPPER, 1);

        PassengerRequest senior = PassengerRequest.of("SeniorPax", 68, "M", BerthType.LOWER);
        SegmentMask mask = SegmentMask.of(0, 4);

        double lowerScore = scorer.score(0L, mask, lowerBerth, senior, List.of(), 10);
        double upperScore = scorer.score(0L, mask, upperBerth, senior, List.of(), 10);

        Assertions.assertTrue(lowerScore > upperScore, "Senior citizen must receive significantly higher score on Lower Berth");
    }

    @Test
    @DisplayName("InventoryPartition: Full lifecycle with Hold, Expiry/Release, and Confirm")
    void testPartitionHoldAndConfirmLifecycle() {
        int totalSegments = 5;
        BerthMeta[] meta = new BerthMeta[]{
            BerthMeta.of(0, "B1", 1, BerthType.LOWER, 1),
            BerthMeta.of(1, "B1", 2, BerthType.MIDDLE, 1)
        };
        InventoryEngine engine = new BerthMajorInventoryEngine(totalSegments, meta);
        QuotaPool pool = new QuotaPool("3A", "GN", engine, 2, 2);

        InventoryPartition partition = new InventoryPartition("12951", "2026-10-22", totalSegments);
        partition.registerQuotaPool(pool);

        UUID bookingId = UUID.randomUUID();
        PassengerRequest pax = PassengerRequest.of("Alice", 28, "F", BerthType.LOWER);

        // 1. Hold
        InventoryCommand.HoldBerthsCommand holdCmd = new InventoryCommand.HoldBerthsCommand(
            1L, Instant.now(), "12951", "2026-10-22", bookingId, "3A", "GN", 0, 3, List.of(pax), 600
        );
        InventoryEvent holdEvent = partition.apply(holdCmd);
        Assertions.assertInstanceOf(InventoryEvent.BerthHeldEvent.class, holdEvent);
        Assertions.assertEquals(1, partition.activeHoldsCount());

        // 2. Confirm
        InventoryCommand.ConfirmBerthsCommand confirmCmd = new InventoryCommand.ConfirmBerthsCommand(
            2L, Instant.now(), "12951", "2026-10-22", bookingId
        );
        InventoryEvent confirmEvent = partition.apply(confirmCmd);
        Assertions.assertInstanceOf(InventoryEvent.BerthConfirmedEvent.class, confirmEvent);
        Assertions.assertEquals(0, partition.activeHoldsCount());
        Assertions.assertEquals(1, partition.confirmedBookingsCount());
    }
}
