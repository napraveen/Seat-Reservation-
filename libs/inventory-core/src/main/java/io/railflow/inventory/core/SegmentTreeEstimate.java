package io.railflow.inventory.core;

/**
 * Segment tree maintaining per-segment free berth counts.
 * Provides O(log S) Range-Minimum Query (RMQ) for rapid display estimates.
 */
public final class SegmentTreeEstimate {

    private final int n;
    private final int[] tree;

    public SegmentTreeEstimate(int totalSegments, int initialCapacityPerSegment) {
        this.n = totalSegments;
        this.tree = new int[4 * Math.max(1, totalSegments)];
        build(1, 0, n - 1, initialCapacityPerSegment);
    }

    private void build(int node, int start, int end, int val) {
        if (start == end) {
            tree[node] = val;
            return;
        }
        int mid = (start + end) / 2;
        build(2 * node, start, mid, val);
        build(2 * node + 1, mid + 1, end, val);
        tree[node] = Math.min(tree[2 * node], tree[2 * node + 1]);
    }

    public int queryRangeMin(int l, int r) {
        if (l > r || l < 0 || r >= n) return 0;
        return query(1, 0, n - 1, l, r);
    }

    private int query(int node, int start, int end, int l, int r) {
        if (r < start || end < l) return Integer.MAX_VALUE;
        if (l <= start && end <= r) return tree[node];
        int mid = (start + end) / 2;
        int leftMin = query(2 * node, start, mid, l, r);
        int rightMin = query(2 * node + 1, mid + 1, end, l, r);
        return Math.min(leftMin, rightMin);
    }

    public void updateSegmentDelta(int segIdx, int delta) {
        if (segIdx < 0 || segIdx >= n) return;
        update(1, 0, n - 1, segIdx, delta);
    }

    private void update(int node, int start, int end, int idx, int delta) {
        if (start == end) {
            tree[node] = Math.max(0, tree[node] + delta);
            return;
        }
        int mid = (start + end) / 2;
        if (idx <= mid) {
            update(2 * node, start, mid, idx, delta);
        } else {
            update(2 * node + 1, mid + 1, end, idx, delta);
        }
        tree[node] = Math.min(tree[2 * node], tree[2 * node + 1]);
    }
}
