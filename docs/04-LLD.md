# 04. Low-Level Design (LLD): The Inventory & Allocation Engine

---

## 1. Segment Representation: The Interval Problem

In Indian Railways, a train with $S$ stops contains $S-1$ individual segments (legs). A berth is **never** sold as a single monolithic unit; it is leased over a contiguous segment interval $[i, j-1]$ where stop $i$ is boarding and stop $j$ is deboarding ($i < j$).

### 1.1 64-Bit Integer Bitmask
Since $>99.9\%$ of Indian Railways trains have $S \le 64$ stops, a journey's occupancy is mapped to a single 64-bit integer (`long` in Java):

```java
/**
 * Segments [fromIdx, toIdx) of a journey represented as a 64-bit bitmask.
 * Bit k is set if the berth is occupied between stop k and stop k+1.
 */
public static long maskOf(int fromIdx, int toIdx) {
    if (fromIdx < 0 || toIdx > 64 || fromIdx >= toIdx) {
        throw new IllegalArgumentException("Invalid segment range: " + fromIdx + " -> " + toIdx);
    }
    return ((1L << (toIdx - fromIdx)) - 1) << fromIdx;
}
```

### 1.2 Elementary Bitwise Operations
For any berth with existing occupancy bitmask `berthMask`:

1. **Exact Fit Check**:
   $$\text{canFit}(\text{berthMask}, \text{reqMask}) \iff (\text{berthMask} \ \& \ \text{reqMask}) == 0$$
   *One CPU `AND` instruction and one comparison. Zero locks. Execution time: $< 2\text{ ns}$.*

2. **Allocate (Book)**:
   $$\text{berthMask} \leftarrow \text{berthMask} \ | \ \text{reqMask}$$

3. **Release (Cancel)**:
   $$\text{berthMask} \leftarrow \text{berthMask} \ \& \ (\sim\text{reqMask})$$

---

## 2. The Segment Tree Trap: Bound vs. Truth

A naive system maintains per-segment free counters (`freeCount[seg]`) and uses a Segment Tree (Range-Minimum-Query, $O(\log S)$) to answer availability:

$$\text{Estimated Availability}(i, j) = \min_{k \in [i, j-1]} \text{freeCount}[k]$$

### The Failure Counterexample
Consider a train with 2 berths and 2 segments:
- **Berth 1**: Sold for Segment 0 (`[0, 1]`). Free on Segment 1 (`berthMask = 0b01`).
- **Berth 2**: Sold for Segment 1 (`[1, 2]`). Free on Segment 0 (`berthMask = 0b10`).

The segment counters report:
$$\text{freeCount}[0] = 1, \quad \text{freeCount}[1] = 1 \implies \min(1, 1) = 1$$

The segment tree reports that **1 full-route berth is available**. In physical reality, **no single berth is free across both segments**! An attempt to book the full route would fail or fragment.

### RailFlow's Resolution
- **Read Path (Search Display)**: Segment trees provide a fast, cached, upper-bound estimate for exploratory searches, labeled with confidence metadata.
- **Write Path (Authoritative Allocation)**: **Always** executes an exact bitmask scan over individual berths. Allocation never operates on estimates.

---

## 3. Storage Layouts: Layout A vs. Layout B

| Property | Layout A (Berth-Major) | Layout B (Segment-Major) |
|---|---|---|
| **Data Structure** | `long[] berthMask` (length $N$) | `BitSet[] segmentOccupancy` (length $S$, each $N$ bits) |
| **Exact Availability Check** | Scan array: `(berthMask[b] & req) == 0` | Bitwise `AND` across $k \in [i, j-1]$ of `~segmentOccupancy[k]` |
| **Complexity** | $O(N)$ simple 64-bit words ($N \approx 1,200 \implies 1.2\ \mu\text{s}$) | $O((j-i) \times \lceil N/64 \rceil)$ word operations |
| **Preference Sorting** | **Native**: Iterate in priority order (LB, UB, etc.) | Requires bit-index translation |
| **SIMD Vectorization** | Direct Vector API (`LongVector`) parallel scanning | BitSet cross-row AND |

RailFlow adopts **Layout A** as its primary engine for deterministic preference traversal and SIMD vectorization.

---

## 4. Best-Fit Bin-Packing Allocation Function

Allocation is not simple assignment. Greedily allocating the first free berth to a short-distance passenger fragments the coach and destroys sellable full-route capacity.

```
Train: NDLS -> BPL -> NGP -> BCT (3 segments).
Berth 1 is virgin (completely empty).
Passenger requests NDLS -> BPL (segment 0).
- Naive first-fit gives Berth 1 to NDLS -> BPL.
- Result: Full-route NDLS -> BCT ticket can no longer be sold on Berth 1!
```

RailFlow optimizes allocation using a configurable multi-objective scoring heuristic:

$$\text{Score}(b, \text{req}) = W_{\text{contig}} \cdot C(b, \text{req}) - W_{\text{frag}} \cdot H(b, \text{req}) + W_{\text{pref}} \cdot P(b, \text{pax}) + W_{\text{group}} \cdot G(b) + W_{\text{policy}} \cdot L(b, \text{pax}) - W_{\text{virgin}} \cdot V(b, \text{req})$$

### Scoring Factors
1. **Contiguity Bonus ($C$)**: Yields $+10$ if the requested interval sits flush against existing bookings, preserving open spans.
2. **Hole / Fragmentation Penalty ($H$)**: Subtracts $-15$ for every isolated 1-segment gap created that is difficult to resell.
3. **Berth Preference ($P$)**: Evaluates request match: Lower (`LB`), Middle (`MB`), Upper (`UB`), Side Lower (`SL`), Side Upper (`SU`).
4. **Group Adjacency ($G$)**: Evaluates whether the berth shares a bay (coupe of 6 or 8) with already-allocated co-passengers.
5. **Social / Railway Policy Bonus ($L$)**: Mandatory rules:
   - Senior citizens ($\ge 60$) granted Lower Berths.
   - Divyangjan passengers granted Lower Berths adjacent to coach doorways.
   - Solo female passengers granted berths in Ladies' Quota or adjacent to other female passengers.
6. **Virgin Berth Protection ($V$)**: Discourages placing a 1-segment passenger onto a completely empty berth if partially-filled berths have capacity.

---

## 5. The Single-Writer Deterministic Actor

Each `(train_id, journey_date)` partition is owned by a single, pinned worker thread inside `inventory-service`:

```java
public final class InventoryPartition {
    private final String trainNumber;
    private final LocalDate journeyDate;
    private final Map<PoolKey, QuotaPool> pools;
    private long appliedOffset;

    public AllocationResult apply(InventoryCommand cmd) {
        // PURE function: No I/O, no wall-clock time, no random numbers.
        // Clock arrives in cmd.issuedAt().
        // Identical log offset prefix produces byte-identical state.
    }
}
```

### Determinism Rules
- **No Wall Clock**: Time is strictly derived from `cmd.issuedAt`.
- **No Randomness**: Lottery ordering is resolved upstream prior to command ingestion.
- **Ordered Collections**: Key sets use `LinkedHashMap` and arrays for deterministic iteration.
- **Recovery**: Snapshot taken every $N=100,000$ commands. Partition recovers from MinIO snapshot + Kafka log replay in $< 2.0\text{ seconds}$.
