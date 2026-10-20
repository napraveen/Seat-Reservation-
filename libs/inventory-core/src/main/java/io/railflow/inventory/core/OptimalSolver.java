package io.railflow.inventory.core;

import java.util.*;

/**
 * Offline optimal interval allocation solver.
 * Solves the maximum-weight interval packing problem using memoized branch-and-bound
 * with symmetry breaking to benchmark RailFlow's online greedy allocator.
 */
public final class OptimalSolver {

    public record IntervalJob(
        int id,
        int fromIdx,
        int toIdx,
        int value
    ) {}

    public static int solveOptimalUtilization(List<IntervalJob> jobs, int numBerths, int totalSegments) {
        if (jobs == null || jobs.isEmpty() || numBerths <= 0) return 0;

        List<IntervalJob> sorted = new ArrayList<>(jobs);
        sorted.sort(Comparator.comparingInt(IntervalJob::fromIdx).thenComparingInt(IntervalJob::toIdx));

        // Precompute remaining upper bound for branch pruning
        int[] suffixSum = new int[sorted.size() + 1];
        for (int i = sorted.size() - 1; i >= 0; i--) {
            suffixSum[i] = suffixSum[i + 1] + sorted.get(i).value();
        }

        Map<String, Integer> memo = new HashMap<>();
        long[] berthOccupancies = new long[numBerths];
        return solveMemoized(sorted, 0, berthOccupancies, suffixSum, memo);
    }

    private static int solveMemoized(
        List<IntervalJob> jobs,
        int index,
        long[] berths,
        int[] suffixSum,
        Map<String, Integer> memo
    ) {
        if (index >= jobs.size()) return 0;

        // Symmetry breaking: sort berths so duplicate occupancy profiles map to the same state
        long[] canonical = berths.clone();
        Arrays.sort(canonical);
        String stateKey = index + ":" + Arrays.toString(canonical);

        Integer cached = memo.get(stateKey);
        if (cached != null) return cached;

        IntervalJob job = jobs.get(index);
        long jobMask = SingleWordMask.maskOf(job.fromIdx(), job.toIdx());

        // Choice 1: Skip this job
        int maxGain = solveMemoized(jobs, index + 1, canonical, suffixSum, memo);

        // Choice 2: Assign job to one of the available berths
        Set<Long> seenOccupancies = new HashSet<>();
        for (int b = 0; b < canonical.length; b++) {
            if ((canonical[b] & jobMask) == 0L && seenOccupancies.add(canonical[b])) {
                canonical[b] |= jobMask;
                int candidate = job.value() + solveMemoized(jobs, index + 1, canonical, suffixSum, memo);
                if (candidate > maxGain) {
                    maxGain = candidate;
                }
                canonical[b] &= ~jobMask; // backtrack
            }
        }

        memo.put(stateKey, maxGain);
        return maxGain;
    }
}
