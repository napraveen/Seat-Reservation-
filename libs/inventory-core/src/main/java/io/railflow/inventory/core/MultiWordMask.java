package io.railflow.inventory.core;

import java.util.Arrays;

/**
 * Arbitrary-length segment mask backed by long[] for rare trains with S > 64 stops.
 */
public final class MultiWordMask implements SegmentMask {

    private final long[] words;
    private final int fromIdx;
    private final int toIdx;

    public MultiWordMask(int fromIdx, int toIdx, int totalSegments) {
        if (fromIdx < 0 || toIdx <= fromIdx) {
            throw new IllegalArgumentException("Invalid segment range: [" + fromIdx + ", " + toIdx + ")");
        }
        this.fromIdx = fromIdx;
        this.toIdx = toIdx;
        int wordCount = (totalSegments + 63) / 64;
        this.words = new long[wordCount];

        for (int i = fromIdx; i < toIdx; i++) {
            int wordIdx = i / 64;
            int bitIdx = i % 64;
            words[wordIdx] |= (1L << bitIdx);
        }
    }

    public MultiWordMask(long[] words, int fromIdx, int toIdx) {
        this.words = words.clone();
        this.fromIdx = fromIdx;
        this.toIdx = toIdx;
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
        return toIdx - fromIdx;
    }

    @Override
    public boolean overlaps(SegmentMask other) {
        if (other instanceof MultiWordMask multi) {
            int minLen = Math.min(words.length, multi.words.length);
            for (int i = 0; i < minLen; i++) {
                if ((this.words[i] & multi.words[i]) != 0) return true;
            }
            return false;
        }
        return (this.words[0] & other.asSingleWord()) != 0;
    }

    @Override
    public SegmentMask union(SegmentMask other) {
        int maxLen = Math.max(words.length, other instanceof MultiWordMask m ? m.words.length : 1);
        long[] result = new long[maxLen];
        System.arraycopy(words, 0, result, 0, words.length);
        if (other instanceof MultiWordMask m) {
            for (int i = 0; i < m.words.length; i++) {
                result[i] |= m.words[i];
            }
        } else {
            result[0] |= other.asSingleWord();
        }
        return new MultiWordMask(result, Math.min(fromIdx, other.fromIdx()), Math.max(toIdx, other.toIdx()));
    }

    @Override
    public SegmentMask difference(SegmentMask other) {
        long[] result = words.clone();
        if (other instanceof MultiWordMask m) {
            int minLen = Math.min(result.length, m.words.length);
            for (int i = 0; i < minLen; i++) {
                result[i] &= ~m.words[i];
            }
        } else {
            result[0] &= ~other.asSingleWord();
        }
        return new MultiWordMask(result, fromIdx, toIdx);
    }

    @Override
    public long asSingleWord() {
        return words.length > 0 ? words[0] : 0L;
    }

    @Override
    public int popCount() {
        int count = 0;
        for (long word : words) {
            count += Long.bitCount(word);
        }
        return count;
    }

    @Override
    public boolean isEmpty() {
        for (long word : words) {
            if (word != 0L) return false;
        }
        return true;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MultiWordMask that)) return false;
        return Arrays.equals(words, that.words);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(words);
    }
}
