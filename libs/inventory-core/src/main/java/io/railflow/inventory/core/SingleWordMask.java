package io.railflow.inventory.core;

import java.util.Objects;

/**
 * 64-bit single-word mask representation for trains with S <= 64 stops (S-1 <= 63 segments).
 * Covers >99.9% of Indian Railways operations.
 */
public final class SingleWordMask implements SegmentMask {

    private final long mask;
    private final int fromIdx;
    private final int toIdx;

    public SingleWordMask(int fromIdx, int toIdx) {
        if (fromIdx < 0 || toIdx > 64 || fromIdx >= toIdx) {
            throw new IllegalArgumentException("Invalid segment range: [" + fromIdx + ", " + toIdx + ")");
        }
        this.fromIdx = fromIdx;
        this.toIdx = toIdx;
        this.mask = maskOf(fromIdx, toIdx);
    }

    public SingleWordMask(long rawMask) {
        this.mask = rawMask;
        if (rawMask == 0) {
            this.fromIdx = 0;
            this.toIdx = 0;
        } else {
            this.fromIdx = Long.numberOfTrailingZeros(rawMask);
            this.toIdx = 64 - Long.numberOfLeadingZeros(rawMask);
        }
    }

    public static long maskOf(int fromIdx, int toIdx) {
        if (fromIdx >= toIdx) return 0L;
        if (toIdx - fromIdx >= 64) return -1L;
        return ((1L << (toIdx - fromIdx)) - 1L) << fromIdx;
    }

    @Override
    public int fromIdx() {
        return fromIdx;
    }

    @Override
    public int toIdx() {
        return toIdx;
    }

    @Override
    public int spanLength() {
        return Math.max(0, toIdx - fromIdx);
    }

    @Override
    public boolean overlaps(SegmentMask other) {
        if (other instanceof SingleWordMask single) {
            return (this.mask & single.mask) != 0;
        }
        return (this.mask & other.asSingleWord()) != 0;
    }

    @Override
    public SegmentMask union(SegmentMask other) {
        return new SingleWordMask(this.mask | other.asSingleWord());
    }

    @Override
    public SegmentMask difference(SegmentMask other) {
        return new SingleWordMask(this.mask & (~other.asSingleWord()));
    }

    @Override
    public long asSingleWord() {
        return mask;
    }

    @Override
    public int popCount() {
        return Long.bitCount(mask);
    }

    @Override
    public boolean isEmpty() {
        return mask == 0L;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SingleWordMask that)) return false;
        return mask == that.mask;
    }

    @Override
    public int hashCode() {
        return Long.hashCode(mask);
    }

    @Override
    public String toString() {
        return "SingleWordMask[" + fromIdx + ".." + toIdx + ", hex=0x" + Long.toHexString(mask) + "]";
    }
}
