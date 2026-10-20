# ADR-0001: Partition the World by (train_id, journey_date)

- **Status**: Accepted
- **Deciders**: Principal Distributed Systems Architect

## Context
A booking in Indian Railways involves allocating physical coach berths on a specific train journey on a specific calendar date. Naive designs partition by `user_id` or `booking_id`, forcing multi-partition two-phase distributed transactions across databases whenever two passengers compete for the same coach berth.

## Options Considered
1. **Partition by `user_id`**: High write dispersion; requires distributed locks across independent inventory shards.
2. **Global Single Database with Row Locks (`SELECT FOR UPDATE`)**: Creates extreme row-level lock contention on hot trains during Tatkal.
3. **Partition by `(train_id, journey_date)`**: Every booking touches exactly one train and date.

## Decision
Route all mutating commands through a composite partition key:
$$\text{partition\_key} = \text{hash}(\text{train\_number}, \text{journey\_date}) \pmod{256}$$
A single dedicated worker thread processes all allocations for that train-date instance sequentially.

## Consequences
- **Positive**: Zero distributed transactions across booking instances. Oversell is physically impossible.
- **Negative**: Hot partitions (e.g., *12951 Mumbai Rajdhani* on Diwali eve).
- **Mitigation**: Edge admission control (D3) sheds 99.9% of redundant load before touching the partition actor.

## How We'd Know We Were Wrong
If multi-leg connecting journeys (Train A $\to$ Train B transfers) represented $> 25\%$ of total Indian Railways bookings, requiring multi-train atomic commits. (In reality, connecting journeys represent $< 1.5\%$ and are handled as two independent saga steps).
