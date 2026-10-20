# ADR-0005: 3-Phase Saga, Transactional Outbox & Reconciliation Reaper

- **Status**: Accepted
- **Deciders**: Principal Distributed Systems Architect

## Context
A major pain point in railway reservations is "Money deducted, booking failed" (F2). This occurs when payment authorization succeeds but the inventory allocation fails, crashes, or drops connection before final commit.

## Options Considered
1. **Direct Synchronous Calls (RPC)**: Booking calls PSP synchronously. Network partitions leave stranded transactions.
2. **Two-Phase Commit (2PC / XA)**: Not supported by payment gateways; creates blocking database locks across wide-area networks.
3. **Orchestrated 3-Phase Saga + Transactional Outbox + Autonomous Reaper**: Break reservation into `HOLD`, `PAY`, and `CONFIRM`. Use local outbox tables to guarantee atomic event publication. Run an asynchronous reaper to reconcile payment-inventory divergence.

## Decision
Implement a 3-Phase Saga with idempotent steps. Outbox events are persisted in the same ACID transaction as the entity state change. A leader-elected background reaper continuously detects orphan payments (captured without confirmed ticket) and issues automatic refunds within 15 minutes.

## Consequences
- **Positive**: Eliminates lost money. Guarantee that financial state and physical berths provably converge.
- **Negative**: Adds eventual consistency complexity and outbox polling overhead.
- **Mitigation**: CDC (Change Data Capture) or high-frequency polling with partial indexes minimizes outbox lag to $< 50\text{ ms}$.

## How We'd Know We Were Wrong
If reconciliation reaper introduced false-positive refunds for delayed payment webhooks that were actively confirming. Hold TTL (600s) provides ample buffer before reaper acts.
