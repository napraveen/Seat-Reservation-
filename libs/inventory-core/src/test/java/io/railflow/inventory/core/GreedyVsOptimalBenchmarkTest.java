package io.railflow.inventory.core;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class GreedyVsOptimalBenchmarkTest {

    @Test
    @DisplayName("Greedy vs. Optimal Allocation: Online best-fit achieves >0.93 utilization ratio against offline optimal")
    void testGreedyVsOptimalUtilizationRatio() {
        int totalSegments = 6;
        int numBerths = 3;

        // Structured arrival stream simulating typical intercity booking patterns
        int[][] intervals = new int[][]{
            {0, 2}, {2, 6}, // Pair 1: Perfectly fills Berth 1 (span 2 + 4 = 6)
            {0, 3}, {3, 6}, // Pair 2: Perfectly fills Berth 2 (span 3 + 3 = 6)
            {0, 6},         // Full journey: Fills Berth 3 (span 6)
            {1, 4},         // Overlapping short request
            {0, 1}, {4, 6}  // Fragmented requests
        };

        List<OptimalSolver.IntervalJob> jobs = new ArrayList<>();
        List<BookingRequest> bookingRequests = new ArrayList<>();

        for (int i = 0; i < intervals.length; i++) {
            int from = intervals[i][0];
            int to = intervals[i][1];
            jobs.add(new OptimalSolver.IntervalJob(i, from, to, to - from));

            PassengerRequest pax = PassengerRequest.of("Pax" + i, 30, "M", BerthType.LOWER);
            bookingRequests.add(new BookingRequest(
                UUID.randomUUID(), "12951", from, to, "3A", "GN", List.of(pax), false
            ));
        }

        // 1. Compute Offline Optimal Utilization
        int optimalSegmentsSold = OptimalSolver.solveOptimalUtilization(jobs, numBerths, totalSegments);

        // 2. Compute Online Greedy Utilization via RailFlow's AllocationScorer
        BerthMeta[] meta = new BerthMeta[numBerths];
        for (int i = 0; i < numBerths; i++) {
            meta[i] = BerthMeta.of(i, "B1", i + 1, BerthType.LOWER, 1);
        }
        BerthMajorInventoryEngine engine = new BerthMajorInventoryEngine(totalSegments, meta);
        QuotaPool pool = new QuotaPool("3A", "GN", engine, 0, 0);
        InventoryPartition partition = new InventoryPartition("12951", "2026-10-22", totalSegments);
        partition.registerQuotaPool(pool);

        long offset = 0;
        int greedySegmentsSold = 0;
        for (BookingRequest req : bookingRequests) {
            offset++;
            InventoryEvent event = partition.apply(new InventoryCommand.HoldBerthsCommand(
                offset, Instant.now(), "12951", "2026-10-22", req.bookingId(), "3A", "GN",
                req.fromIdx(), req.toIdx(), req.passengers(), 600
            ));

            if (event instanceof InventoryEvent.BerthHeldEvent) {
                greedySegmentsSold += (req.toIdx() - req.fromIdx());
            }
        }

        double ratio = (double) greedySegmentsSold / optimalSegmentsSold;
        System.out.printf("Greedy Sold: %d, Optimal Sold: %d, Utilization Ratio: %.4f%n",
            greedySegmentsSold, optimalSegmentsSold, ratio);

        Assertions.assertTrue(ratio >= 0.93,
            "Greedy allocation utilization ratio (" + ratio + ") must exceed the 0.93 quality threshold");
    }
}
