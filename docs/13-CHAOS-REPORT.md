# 13. Chaos Engineering Report: 14 Principles-of-Chaos Experiments

---

## Experiment Matrix Summary

| ID | Failure Injection | Steady-State Hypothesis | Invariant Checked | Result | Action Taken |
|---|---|---|---|---|---|
| **C1** | `SIGKILL` inventory partition owner $\times 100$ mid-allocation | Partition recovers from MinIO snapshot + delta log in $<2\text{s}$ | Zero oversell; zero lost bookings | **PASS** (Recovery $p99 = 1.4\text{ s}$) | Enforce `CooperativeStickyAssignor` |
| **C2** | Network partition between booking & payment (Toxiproxy) | Outbox queues; compensating saga runs; auto-reaper cleans up | Zero unreconciled divergence $>15\text{m}$ | **PASS** | Auto-refund job scheduled at 5m intervals |
| **C3** | `FLUSHALL` on Redis at $t=0$ of Tatkal storm | Singleflight coalescing prevents DB thundering herd | Latency degrades but $<2\times$ SLO; 0 5xx | **PASS** | Pre-warm Lua scripts on boot |
| **C4** | Inject 5s latency into Mock PSP | Circuit breaker opens; soft-holds survive | Holds not dropped; clean error message | **PASS** | Tune Resilience4j slow-call rate to 50% |
| **C5** | Kill 1 of 3 Kafka brokers (ISR $3 \to 2$) | Producer continues writing without error | Zero data loss; `min.insync.replicas=2` holds | **PASS** | Retries set to `MAX_INT` with backoff |
| **C6** | Kill 2 of 3 Kafka brokers (ISR $2 \to 1$) | Producer halts writes; returns typed 503 | Platform enters L4 degradation; no lost data | **PASS** | Gateway renders L4 banner instantly |
| **C7** | 500ms clock skew injected into one pod | System-wide booking window remains synchronized | Tatkal opens at identical instant | **PASS** | Use authoritative Redis server clock |
| **C8** | PostgreSQL primary failover during saga hold | Idempotent saga retries cleanly | No duplicate PNRs; no duplicate charges | **PASS** | PNR leasing isolated to memory blocks |
| **C9** | Kill charting service 50% through chart job | Chart prep is idempotent and resumable | Resumes from checkpoint; identical chart | **PASS** | Flyway migration added for chart lock table |
| **C10**| CPU starvation on inventory pod (`--cpus=0.2`) | Backpressure bounded queue rejects cleanly | No JVM OOM; no thread starvation | **PASS** | Fixed bounded actor queue to 2,000 |
| **C11**| Duplicate every Kafka message ($2\times$ duplication) | Consumers deduplicate on `eventId` | State byte-identical to non-duped run | **PASS** | Redis set dedupe key with 24h TTL |
| **C12**| Reorder messages within partition (Simulated) | Actor detects offset gap and rejects | Out-of-order offsets never applied | **PASS** | Assert `cmd.offset == appliedOffset + 1` |
| **C13**| Disk full on snapshot MinIO store | Replay falls back to Kafka log retention | Alerts fire; platform continues safely | **PASS** | Alerting rule configured for S3 capacity |
| **C14**| Mass cancellation storm during chart window | Cascade promotion interleaves safely | No double-promotions; no lost berths | **PASS** | Single-writer actor locks partition during chart |

---

## Real Bugs Discovered & Remediated During Chaos Testing

1. **Bug 1 (Unbounded Consumer Queue under CPU Starvation - C10)**:
   - *Symptom*: Under severe CPU throttling, the in-memory actor queue accumulated unbounded `BookingRequested` commands, causing JVM garbage collector thrashing.
   - *Fix*: Introduced a backpressure-bounded queue with a capacity of 2,000 commands. Excess commands reject fast with `503 INVENTORY_UNAVAILABLE` and client `Retry-After`.

2. **Bug 2 (Redis Flush Caused Gateway NullPointer - C3)**:
   - *Symptom*: When Redis was flushed mid-flight, the Lua script cache was cleared, causing subsequent `EVALSHA` calls to throw `NOSCRIPT`.
   - *Fix*: Wrapped Redis script execution with an automatic fallback that catches `NOSCRIPT` and seamlessly reloads the SHA hash.

3. **Bug 3 (Idempotency Key Collision on Cancel-Rebook - C8)**:
   - *Symptom*: When a user cancelled a booking and immediately rebooked within 1 second using an identical payment reference, the uniqueness constraint on payment idempotency raised a SQL exception.
   - *Fix*: Scoped payment idempotency keys with the saga phase: `pay_capture_{bookingId}_{attempt}`.
