# 10. Observability: Metrics, Tracing, Exemplars & Dashboards

---

## 1. Golden Metrics Architecture

RailFlow structures its metrics into three operational tiers:
1. **RED Tier (Rate, Errors, Duration)**: Evaluated per API endpoint and gRPC/Kafka consumer interface.
2. **USE Tier (Utilization, Saturation, Errors)**: Evaluated per infrastructure resource (JVM Virtual Threads, HikariCP connections, Kafka partition lags, Redis memory).
3. **Domain Invariants Tier**: Business health metrics driving on-call alerts.

### 1.1 The Domain Invariant Metrics

```
+----------------------------------------------------------------------------------------+
| Metric Name                                   | Type      | Normal Value | Alert Trigger|
+----------------------------------------------------------------------------------------+
| railflow_oversell_total                       | Counter   | 0            | > 0 (P0 Page)|
| railflow_payment_ticket_divergence            | Gauge     | 0            | > 0 for >15m |
| railflow_admission_shed_ratio                 | Gauge     | 0.0 - 0.999  | > 0.9995     |
| railflow_allocation_duration_microseconds     | Histogram | p99 < 15 µs  | p99 > 100 µs |
| railflow_partition_lag                        | Gauge     | < 50 msgs    | > 500 msgs   |
| railflow_partition_queue_depth                | Gauge     | < 20 msgs    | > 200 msgs   |
| railflow_availability_staleness_ms            | Histogram | p99 < 250 ms | p99 > 1000 ms|
| railflow_hold_expiry_total                    | Counter   | Monotonic    | Spike > 50%  |
| railflow_fairness_winrate_by_rtt_bucket       | Gauge     | Ratio ~ 1.0  | Skew > 1.5x  |
| railflow_degradation_level                    | Gauge     | 0 (L0)       | >= 3 (L3)    |
| railflow_coalesce_ratio                       | Gauge     | 50x - 200x   | < 10x        |
+----------------------------------------------------------------------------------------+
```

---

## 2. Distributed Tracing Continuity

Every reservation saga executes under a unified OpenTelemetry trace traversing the entire distributed topology:

```
Browser (W3C traceparent)
  │
  ▼
api-gateway (Injects span: gateway.admission)
  │
  ▼
Kafka Topic: inventory.commands.v1 (Propagates traceparent in RecordHeaders)
  │
  ▼
inventory-service (Span: inventory.allocate_berth, tags: train_id, date, quota)
  │
  ▼
booking-service (Span: booking.saga_step_hold)
  │
  ▼
Mock PSP (Span: payment.authorize_intent)
  │
  ▼
booking-service (Span: booking.confirm_ticket)
```

### Trace Exemplars in Prometheus
Prometheus histograms record trace IDs as exemplars on latency buckets. When inspecting a $p99$ tail latency spike in Grafana, operators click directly on the outlier data point to jump to the exact offending Tempo trace.

---

## 3. Pre-Provisioned Grafana Dashboards
- **Executive Overview**: Total bookings/sec, admission shed %, revenue settled, active error budget.
- **Tatkal War Room**: Live real-time admission counters, burst arrival velocity, lottery window state, win-rate skew by network RTT bucket.
- **Inventory Engine Internals**: Per-partition actor queue depth, microsecond allocation duration histograms, snapshot age, Kafka consumer group lag.
- **Saga & Financial Health**: State distribution (Held vs Pending vs Confirmed), orphan hold sweeps, payment-ticket divergence gauge.
