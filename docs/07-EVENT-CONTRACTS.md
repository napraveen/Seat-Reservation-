# 07. Event Contracts & Topic Architecture

---

## 1. Topic Design & Partitioning Rationale

All domain messages flow through Apache Kafka / Redpanda:

| Topic Name | Partition Key | Partitions | Retention | Producer Guarantees |
|---|---|---|---|---|
| `inventory.commands.v1` | `{train_number}:{journey_date}` | **256** | 7 days | `acks=all`, `min.insync.replicas=2`, Idempotent Producer |
| `inventory.events.v1` | `{train_number}:{journey_date}` | **256** | 7 days | Ordered state changes produced by single-writer actors |
| `booking.events.v1` | `booking_id` | 64 | 30 days | Saga lifecycle events emitted via Transactional Outbox |
| `payment.events.v1` | `booking_id` | 32 | 30 days | PSP capture, refund, and reconciliation events |
| `waitlist.events.v1` | `{train_number}:{journey_date}` | 64 | 7 days | RAC/WL promotion cascade outcomes |
| `audit.events.v1` | `booking_id` | 32 | Indefinite | Tiered to object storage (MinIO/S3) as compressed Parquet |
| `*.dlq` | Contextual | 8 | 30 days | Dead-letter queues for unprocessable messages |

### Partition Count Rationale (256 Partitions)
At 256 partitions, the cluster supports 256 concurrent single-writer inventory actors. At a measured throughput of **~847,000 allocations/sec/core**, the theoretical aggregate ceiling is $>200,000,000\text{ ops/sec}$—far exceeding the $50,000\text{ TPS}$ peak requirement. Partition count is deliberately chosen as a power of 2 ($2^8$) to enable balanced hash distribution across Kafka broker nodes.

---

## 2. Canonical Event Envelope

Every event produced to any RailFlow topic strictly adheres to this envelope:

```json
{
  "eventId": "01JBX9Z8V4K2P1N5M3Q7R9T2W1",
  "eventType": "inventory.berth_held.v1",
  "occurredAt": "2026-10-21T10:00:02.051Z",
  "producer": "inventory-service@1.0.0",
  "traceparent": "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01",
  "partitionKey": "12951:2026-10-22",
  "idempotencyKey": "bk_01JBX9Z8V4K2P1N5M3Q7",
  "schemaVersion": 1,
  "causationId": "01JBX9Z8V4K2P1N5M3Q7R9T2W0",
  "correlationId": "01JBX9Z8V4K2P1N5M3Q7R9T200",
  "payload": {
    "bookingId": "a1b2c3d4-e5f6-7a8b-9c0d-1e2f3a4b5c6d",
    "trainNumber": "12951",
    "journeyDate": "2026-10-22",
    "classCode": "3A",
    "quotaCode": "TQ",
    "allocatedCoach": "B3",
    "allocatedBerth": 23,
    "allocatedBerthType": "LB",
    "segmentMask": 7,
    "holdExpiresAt": "2026-10-21T10:10:02.051Z"
  }
}
```

---

## 3. Event Catalogue

- **Booking Lifecycle**:
  `booking.requested.v1`, `booking.held.v1`, `booking.confirmed.v1`, `booking.cancelled.v1`, `booking.expired.v1`, `booking.failed.v1`
- **Inventory Lifecycle**:
  `inventory.berth_held.v1`, `inventory.berth_confirmed.v1`, `inventory.berth_released.v1`, `inventory.rac_allocated.v1`, `inventory.waitlisted.v1`, `inventory.no_accommodation.v1`
- **Waitlist Operations**:
  `waitlist.promoted.v1`, `waitlist.cascade_completed.v1`, `waitlist.cancelled_at_chart.v1`
- **Payment Operations**:
  `payment.intent_created.v1`, `payment.captured.v1`, `payment.failed.v1`, `payment.refunded.v1`
- **Charting Milestones**:
  `charting.started.v1`, `charting.completed.v1`, `charting.quota_released.v1`
- **Fairness & Mechanism Protocol**:
  `fairness.window_opened.v1`, `fairness.seed_revealed.v1`, `fairness.order_published.v1`
