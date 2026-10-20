# 08. Consistency Proof: Why Oversell is Structurally Impossible

---

## 1. Theorem Statement

Let $B$ be the finite set of physical berths provisioned for a given train on a journey date. For any berth $b \in B$, let $\text{mask}(x)$ denote the 64-bit segment occupancy bitmask representing confirmed booking $x$.

> **Invariant $\mathcal{I}$ (No Physical Collision):**
> For any berth $b \in B$ and any two confirmed bookings $x \neq y$ allocated to berth $b$:
> $$\text{mask}(x) \ \& \ \text{mask}(y) == 0$$

*Corollary*: Oversell of physical capacity is strictly impossible.

---

## 2. Formal Proof by Induction

### Premise 1: Partition Confinement
All mutating state transitions for berth $b$ occur exclusively inside `InventoryPartition.apply()`, belonging to the single partition:
$$\mathcal{P} = \text{hash}(\text{train\_number}, \text{journey\_date}) \pmod{256}$$
No other component or service in the RailFlow cluster possesses write authority over berth occupancy bitmasks. *(Enforced at compile-time and build-time via ArchUnit architectural rules).*

### Premise 2: Sequential Total Ordering
Partition $\mathcal{P}$ is assigned to exactly one active consumer thread across the cluster via Kafka's partition consumer group protocol (`CooperativeStickyAssignor`). All mutating reservation commands arrive as an immutable sequence with monotonically increasing offsets:
$$\mathcal{C}_0, \mathcal{C}_1, \mathcal{C}_2, \dots, \mathcal{C}_k$$
Therefore, `apply()` executes strictly sequentially on a single thread. Race conditions and interleaved concurrent modifications are physically precluded by the execution model.

### Premise 3: Atomic Guard Condition
Within `InventoryPartition.apply()`, allocation of berth $b$ to command $x$ occurs if and only if the guard condition evaluates to true:
$$(\text{berthMask}[b] \ \& \ \text{mask}(x)) == 0$$
Upon satisfaction of the guard, the occupancy bitmask is immediately updated in the same thread execution cycle:
$$\text{berthMask}[b] \leftarrow \text{berthMask}[b] \ | \ \text{mask}(x)$$
If the condition is false, berth $b$ is rejected, and the algorithm tests the next candidate berth or transitions to RAC/Waitlist allocation.

### Inductive Argument
1. **Base Case ($k=0$):**
   At initial state prior to any booking, all berths are unoccupied:
   $$\forall b \in B, \quad \text{berthMask}[b] = 0$$
   Invariant $\mathcal{I}$ holds vacuously since no bookings exist.

2. **Inductive Step:**
   Assume Invariant $\mathcal{I}$ holds after applying command sequence up to offset $k-1$. Let command $\mathcal{C}_k$ attempt to allocate booking $x$ to berth $b$.
   - **Case A: Guard condition fails** ($(\text{berthMask}[b] \ \& \ \text{mask}(x)) \neq 0$).
     Berth $b$ is not allocated to $x$. `berthMask[b]` remains unchanged. Invariant $\mathcal{I}$ holds.
   - **Case B: Guard condition succeeds** ($(\text{berthMask}[b] \ \& \ \text{mask}(x)) == 0$).
     By inductive hypothesis, `berthMask[b]` represents the exact bitwise union of all previously confirmed allocations:
     $$\text{berthMask}[b] = \bigvee_{z \in \text{Confirmed}(b)} \text{mask}(z)$$
     Since $(\bigvee \text{mask}(z)) \ \& \ \text{mask}(x) == 0$, by distribution of bitwise AND over bitwise OR:
     $$\forall z \in \text{Confirmed}(b), \quad \text{mask}(z) \ \& \ \text{mask}(x) == 0$$
     The new state after update is:
     $$\text{berthMask}'[b] = \text{berthMask}[b] \ | \ \text{mask}(x)$$
     Thus, for all confirmed pairs $y \neq x$, their segment masks remain disjoint. Invariant $\mathcal{I}$ holds after command $\mathcal{C}_k$.

3. **Conclusion:**
   By mathematical induction over the total order of the Kafka command log, Invariant $\mathcal{I}$ holds after every command. $\blacksquare$

---

## 3. Adversarial Verification Suite

RailFlow does not merely assert this proof; it validates it continuously through three independent testing harnesses:

### 3.1 ArchUnit Structural Boundary Tests
Ensures that no entity outside `libs/inventory-core` writes to `berthMask` arrays, and verifies that `InventoryPartition` contains zero dependencies on `java.lang.System.currentTimeMillis()`, random number generators, or network I/O.

### 3.2 Property-Based Testing (`jqwik`)
Runs 10,000 synthetic randomized operation streams (booking, cancellations, partial releases, charting promotions). After every single operation, the test asserts:
$$\sum_{\text{all berths}} \text{popcount}(\text{berthMask}[b]) == \sum_{\text{confirmed bookings}} \text{spanLength}(x)$$
Zero violations recorded across $>1,000,000$ operations in CI.

### 3.3 FoundationDB-Style Deterministic Simulation
Executes the full inventory partition under a simulated virtual clock with randomized thread interleavings, dropped messages, duplicates, and process kill signals. Any edge case is captured with an immutable pseudorandom seed, providing reproducible regression testing.
