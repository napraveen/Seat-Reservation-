# 11. SRE Runbook: On-Call Playbooks, Tatkal Freeze & Disaster Recovery

---

## 1. Multi-Window Multi-Burn-Rate Alerting Rules

Following Google SRE best practices, alerts are triggered based on consumption rate of the 28-day error budget ($0.05\%$ allowed failure $\implies 99.95\%$ SLO):

| Alert Severity | Burn Rate | Budget Consumed | Short Window | Long Window | Action & Escalation |
|---|---|---|---|---|---|
| **P0 (Critical Page)** | $14.4\times$ | $2\%$ in 1 hour | 5 minutes | 1 hour | Immediate engineer page; engage incident commander |
| **P1 (Page)** | $6.0\times$ | $5\%$ in 6 hours | 30 minutes | 6 hours | Page on-call engineer; review degradation ladder |
| **P2 (Ticket)** | $1.0\times$ | $10\%$ in 3 days | 2 hours | 3 days | Automated ticket created for next business day |

---

## 2. On-Call Playbooks

### Playbook 1: `railflow_oversell_total > 0` (Severity: P0 - Immediate Page)
- **Root Cause Hypothesis**: ArchUnit boundary breach, concurrent partition writers, or corrupt snapshot.
- **Diagnostic Steps**:
  1. Inspect Grafana *Tatkal War Room* for the specific `train_number` and `journey_date`.
  2. Query database invariant check:
     ```sql
     SELECT allocated_coach, allocated_berth, COUNT(*) 
     FROM passenger p JOIN booking b ON p.booking_id = b.booking_id
     WHERE b.train_number = '12951' AND b.journey_date = '2026-10-22' AND b.status = 'CONFIRMED'
     GROUP BY allocated_coach, allocated_berth HAVING COUNT(*) > 1;
     ```
  3. If double-allocation detected:
     - Invoke emergency kill switch: `POST /api/v1/admin/killswitch/12951:2026-10-22`.
     - System auto-promotes the second booking to an executive VIP quota berth (`HO`) or issues full compensation.

### Playbook 2: `railflow_payment_ticket_divergence > 0` for $>15\text{ min}$ (Severity: P1)
- **Root Cause Hypothesis**: `booking-service` outbox relay thread crashed or PSP webhook dropped without retry.
- **Remediation**:
  1. Trigger manual run of the reconciliation reaper:
     ```bash
     curl -X POST http://localhost:8080/api/v1/admin/reconciliation/run
     ```
  2. Inspect reaper audit log for refunded orphan transactions. All orphan captures older than 15 minutes are credited back via mock PSP refund API automatically.

### Playbook 3: Partition Consumer Lag Spike ($>500\text{ messages}$) (Severity: P2)
- **Diagnostic Steps**: Check CPU saturation on the partition host container.
- **Action**: Check if JVM virtual threads are pinned on `synchronized` blocks. If pinned threads detected, trigger automated service restart or increase carrier parallelism.

---

## 3. Daily Tatkal Operational Freeze
- **Freeze Period**: **09:00:00 to 12:00:00 IST daily**.
- **Rules**:
  - No continuous integration deployments, schema migrations, or infrastructure updates.
  - SRE on-call stationed in active monitoring room at 09:45:00 IST.
  - Admission counters pre-seeded in Redis at 09:50:00 IST.
  - Freeze lifts automatically at 12:00:00 IST upon zero error budget burn.

---

## 4. Disaster Recovery (DR) RTO & RPO Targets
- **Recovery Point Objective (RPO)**: **0 ms** for confirmed bookings (replicated synchronously to Kafka with `min.insync.replicas=2` and PostgreSQL WAL).
- **Recovery Time Objective (RTO)**: $< 2.0\text{ seconds}$ per partition (snapshot reload from MinIO + delta log replay).
