package io.railflow.inventory.core;

import java.util.List;

/**
 * Layout B: Segment-Major in-memory allocation engine.
 * Occupancy is stored as segmentOccupancy[segment][wordIdx].
 * Benchmarked alongside Layout A.
 */
public final class SegmentMajorInventoryEngine implements InventoryEngine {

    private final int totalBerths;
    private final int totalSegments;
    private final int numWords;
    private final long[][] segmentOccupancy; // [seg][word]
    private final BerthMeta[] berthMeta;

    public SegmentMajorInventoryEngine(int totalSegments, BerthMeta[] metadata) {
        this.totalSegments = totalSegments;
        this.totalBerths = metadata.length;
        this.numWords = (totalBerths + 63) / 64;
        this.segmentOccupancy = new long[totalSegments][numWords];
        this.berthMeta = metadata.clone();
    }

    @Override
    public int countAvailableBerths(SegmentMask mask) {
        int count = 0;
        int from = mask.fromIdx();
        int to = mask.toIdx();

        for (int w = 0; w < numWords; w++) {
            long occupiedInSpan = 0L;
            for (int s = from; s < to; s++) {
                occupiedInSpan |= segmentOccupancy[s][w];
            }
            long freeWord = ~occupiedInSpan;
            if (w == numWords - 1 && (totalBerths % 64 != 0)) {
                freeWord &= ((1L << (totalBerths % 64)) - 1L);
            }
            count += Long.bitCount(freeWord);
        }
        return count;
    }

    @Override
    public boolean canFitAny(SegmentMask mask) {
        int from = mask.fromIdx();
        int to = mask.toIdx();

        for (int w = 0; w < numWords; w++) {
            long occupiedInSpan = 0L;
            for (int s = from; s < to; s++) {
                occupiedInSpan |= segmentOccupancy[s][w];
            }
            long freeWord = ~occupiedInSpan;
            if (w == numWords - 1 && (totalBerths % 64 != 0)) {
                freeWord &= ((1L << (totalBerths % 64)) - 1L);
            }
            if (freeWord != 0L) return true;
        }
        return false;
    }

    @Override
    public int allocateBestFit(
        SegmentMask requestMask,
        PassengerRequest passenger,
        List<BerthMeta> partyAllocated,
        AllocationScorer scorer
    ) {
        int from = requestMask.fromIdx();
        int to = requestMask.toIdx();
        int bestIdx = -1;
        double bestScore = Double.NEGATIVE_INFINITY;

        for (int b = 0; b < totalBerths; b++) {
            int w = b / 64;
            int bit = b % 64;
            long bitMask = 1L << bit;

            boolean fits = true;
            for (int s = from; s < to; s++) {
                if ((segmentOccupancy[s][w] & bitMask) != 0) {
                    fits = false;
                    break;
                }
            }

            if (fits) {
                long existingMask = getOccupancy(b);
                double s = scorer.score(existingMask, requestMask, berthMeta[b], passenger, partyAllocated, totalSegments);
                if (s > bestScore) {
                    bestScore = s;
                    bestIdx = b;
                }
            }
        }

        if (bestIdx != -1) {
            allocateSpecific(bestIdx, requestMask);
        }
        return bestIdx;
    }

    @Override
    public boolean allocateSpecific(int berthIdx, SegmentMask mask) {
        if (berthIdx < 0 || berthIdx >= totalBerths) return false;
        int w = berthIdx / 64;
        long bitMask = 1L << (berthIdx % 64);
        int from = mask.fromIdx();
        int to = mask.toIdx();

        for (int s = from; s < to; s++) {
            if ((segmentOccupancy[s][w] & bitMask) != 0) return false;
        }
        for (int s = from; s < to; s++) {
            segmentOccupancy[s][w] |= bitMask;
        }
        return true;
    }

    @Override
    public void release(int berthIdx, SegmentMask mask) {
        if (berthIdx < 0 || berthIdx >= totalBerths) return;
        int w = berthIdx / 64;
        long clearMask = ~(1L << (berthIdx % 64));
        int from = mask.fromIdx();
        int to = mask.toIdx();

        for (int s = from; s < to; s++) {
            segmentOccupancy[s][w] &= clearMask;
        }
    }

    @Override
    public long getOccupancy(int berthIdx) {
        if (berthIdx < 0 || berthIdx >= totalBerths) return -1L;
        int w = berthIdx / 64;
        long bitMask = 1L << (berthIdx % 64);
        long mask = 0L;
        for (int s = 0; s < Math.min(totalSegments, 64); s++) {
            if ((segmentOccupancy[s][w] & bitMask) != 0) {
                mask |= (1L << s);
            }
        }
        return mask;
    }

    @Override
    public int totalBerths() {
        return totalBerths;
    }

    @Override
    public BerthMeta getMeta(int berthIdx) {
        return berthMeta[berthIdx];
    }
}
