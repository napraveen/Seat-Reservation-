# ADR-0002: In-Memory Replicated State Machine with Event Sourcing

- **Status**: Accepted
- **Deciders**: Principal Distributed Systems Architect

## Context
Traditional systems treat the relational database as the single source of truth for seat allocation. With 10,000 users per second competing for 50 berths, database connection pools exhaust and transactions timeout.

## Options Considered
1. **Relational DB as Source of Truth with Optimistic Locking**: Fails with 99% optimistic lock retry exceptions under peak contention.
2. **Pessimistic Row Locking (`SELECT FOR UPDATE`)**: Saturates database thread pools; average lock hold times cascade from 2ms to 2000ms.
3. **In-Memory Replicated State Machine (Pure Fold over Kafka Log)**: Evaluates segment bitmasks in microsecond CPU memory; writes commands to Kafka first.

## Decision
The authoritative source of truth is the ordered Kafka command log (`inventory.commands.v1`). Inventory state is maintained in RAM as an event-sourced fold:
$$\text{State}_t = \text{State}_0 \circ \mathcal{C}_1 \circ \mathcal{C}_2 \circ \dots \circ \mathcal{C}_t$$
Relational PostgreSQL tables are strictly asynchronous materialized projections.

## Consequences
- **Positive**: Exact berth allocation completes in $1.2\ \mu\text{s}$. Microsecond response times allow running thousands of allocations per second per core.
- **Negative**: Crash recovery requires reloading snapshots and replaying Kafka offsets.
- **Mitigation**: Periodic snapshots every 100,000 commands allow complete partition restore in $< 2.0\text{ seconds}$.

## How We'd Know We Were Wrong
If full-network inventory size exceeded available physical RAM (e.g., $> 256\text{ GB}$). At $4.8\text{ GB}$, the entire 60-day network inventory easily fits in RAM.
