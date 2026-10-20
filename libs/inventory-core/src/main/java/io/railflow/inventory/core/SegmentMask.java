package io.railflow.inventory.core;

/**
 * Represents a journey segment interval as a bitmask.
 * A train with S stops has S-1 legs/segments.
 * Journey from stop i to stop j (i < j) occupies segments [i, j-1].
 */
public sealed interface SegmentMask permits SingleWordMask, MultiWordMask {

    static SegmentMask of(int fromIdx, int toIdx, int totalStops) {
        if (totalStops <= 65) {
            return new SingleWordMask(fromIdx, toIdx);
        } else {
            return new MultiWordMask(fromIdx, toIdx, totalStops - 1);
        }
    }

    static SegmentMask of(int fromIdx, int toIdx) {
        return new SingleWordMask(fromIdx, toIdx);
    }

    int fromIdx();
    int toIdx();
    int spanLength();
    boolean overlaps(SegmentMask other);
    SegmentMask union(SegmentMask other);
    SegmentMask difference(SegmentMask other);
    long asSingleWord();
    int popCount();
    boolean isEmpty();
}
