# 01. Requirements, Service Level Objectives & Error Budgets

---

## 1. Functional Requirements (FR)

### Search & Discovery
- **FR-1 Station Autocomplete**: Station search by 3-4 letter code (e.g. `NDLS`), full name (`New Delhi`), or phonetic/fuzzy match, with $p99 < 50\text{ ms}$.
- **FR-2 Train Search**: Multi-class and multi-quota search between origin and destination stations on a specified date with real-time availability indicator.
- **FR-3 Availability Calendar**: 7-day rolling window availability matrix for a selected train and travel class.
- **FR-4 Dynamic Fare Breakdown**: Itemized fare calculation prior to booking: Base Fare + Reservation Fee + Superfast Charge + Dynamic Flexi-multiplier + Catering + 5% GST on AC classes.
- **FR-5 Alternate Suggestions**: Suggest nearby stations (e.g., `NZM`/`DLI` when `NDLS` is chosen), alternate dates, and VIKALP-style alternate train routes.

### Booking & Inventory
- **FR-6 Multi-Passenger Booking**: Up to 6 passengers per booking on General quota, strictly capped at 4 passengers for Tatkal quota, with per-passenger berth preference (Lower, Middle, Upper, Side Lower, Side Upper).
- **FR-7 Multi-Quota Allocation Algebra**: Segregated inventory pools supporting `GN` (General), `TQ` (Tatkal), `PT` (Premium Tatkal), `LD` (Ladies), `SS` (Senior Citizen), `HP` (Divyangjan), `DF` (Defence), `FT` (Foreign Tourist), `PQ` (Pooled Quota), `RL` (Remote Location), `HO` (Headquarters/VIP), and `YU` (Yuva).
- **FR-8 Orchestrated Reservation Saga**: Two-phase reservation with state transition `HELD` $\to$ `PAYMENT_PENDING` $\to$ `CONFIRMED`. Hard TTL on soft-holds (default 600s).
- **FR-9 Strict Idempotency**: All mutating operations enforce idempotency via `Idempotency-Key` header with 24-hour persistence.
- **FR-10 Policy & Adjacency Constraints**: Automated allocation algorithms honor senior-citizen lower berths, Divyangjan proximity to doors, ladies' coach grouping, and passenger group co-location within the same bay.
- **FR-11 Reservation Against Cancellation (RAC) & Waitlist Hierarchy**: Automated fallback to RAC when confirmed berths are exhausted. Dynamic assignment of precise waitlist types: `GNWL`, `RLWL`, `PQWL`, `TQWL`, or `RSWL`.
- **FR-12 Auto-Upgradation & Alternate Train Selection**: Opt-in flag for automated class promotion (e.g., Sleeper to 3A) during chart preparation when higher-class berths are vacant.

### Post-Booking & Charting
- **FR-13 PNR Status Lookup**: Unauthenticated, cached, high-throughput status query returning passenger current status vs. booking status with $p99 < 80\text{ ms}$.
- **FR-14 Cancellation & Cascade Engine**: Table-driven cancellation refund slabs ($>48\text{h}$, $48\text{--}12\text{h}$, $12\text{--}4\text{h}$, $<4\text{h}$) triggering an immediate waitlist promotion cascade.
- **FR-15 Chart Preparation**: Automated, idempotent charting jobs at $T-4\text{h}$ and $T-30\text{m}$. Unused quotas released to the general pool; final RAC-to-Confirmed and WL-to-RAC promotions applied; unconfirmed waitlists auto-cancelled and auto-refunded.
- **FR-16 Current Booking**: Post-chart unsold berth inventory made reservable until 30 minutes before departure.
- **FR-17 Immutable Audit Trail**: Complete append-only event lineage for every PNR transition.

### Platform & Mechanism Design
- **FR-18 Truthful Virtual Queue**: Streaming real-time queue position and calculated wait time via Server-Sent Events (SSE).
- **FR-19 Verifiable Randomness Anti-Tout Lottery**: 2,000ms collection window with committed seed revealed post-close to eliminate low-latency script advantages.
- **FR-20 Anti-Abuse & Rate Controls**: Identity-level booking velocity caps, browser cryptographic device attestation (WebCrypto Ed25519), and Proof-of-Work challenge escalations.
- **FR-21 Verified-Identity Tatkal & Agent Lockout**: Aadhaar OTP verification step-up and 30-minute commercial agent lockout policy filters.
- **FR-22 SRE Admin Control Deck**: Live feature flags, multi-level degradation ladder (L0–L5), circuit breaker overrides, and partition inspection.
- **FR-23 Deterministic Tatkal Storm Simulator**: Workload injector driving realistic synthetic human vs. tout workloads for live validation and chaos testing.

---

## 2. Explicit Non-Goals
1. **Seat-Level Revenue Management / Surge Pricing AI**: Complex dynamic pricing beyond the published Indian Railways Flexi-Fare slab formulas.
2. **Third-Party Payment Gateway Integration**: Real bank APIs; replaced with a deterministic, high-throughput Mock PSP supporting configurable network latencies and failure injection.
3. **Real UIDAI / Aadhaar Server Integration**: Real biometric or OTP endpoints; mocked via deterministic Ed25519 signature checks and cryptographically verifiable mock challenge tokens.
4. **Freight, Unreserved (UTS), and Parcel Logistics**: Subsystem restricted to the PRS reserved coach inventory control plane.
5. **Cross-Continental Multi-Region Active-Active**: Operating as a single-region multi-AZ distributed architecture with documented disaster recovery procedures.

---

## 3. Service Level Objectives (SLOs) & Error Budgets

Evaluated over a rolling 28-day window:

| Service / Interface | Metric (SLI) | SLO Target | Window | Rationale |
|---|---|---|---|---|
| **Search & Availability** | Latency ($p99$) | $< 150\text{ ms}$ | 28 days | Served from bounded-staleness L1/L2 read models |
| **Search & Availability** | Latency ($p99.9$) | $< 400\text{ ms}$ | 28 days | Tail latency during heavy multi-segment searches |
| **Booking Initiate (Hold)** | Latency ($p99$) | $< 300\text{ ms}$ | 28 days | Serialized reservation actor processing |
| **Booking Confirm (Post-Pay)**| Latency ($p99$) | $< 800\text{ ms}$ | 28 days | Includes PSP webhook callback round-trip |
| **PNR Status Lookup** | Latency ($p99$) | $< 80\text{ ms}$ | 28 days | Highest-volume public enquiry endpoint |
| **Availability Read Staleness**| Max Age ($p99$) | $< 250\text{ ms}$ | 28 days | Bounded-staleness CQRS projection lag |
| **Availability Read Staleness**| Hard Ceiling | $\le 1,000\text{ ms}$| All time | If exceeded, response marked `degraded` |
| **Platform Availability** | Successful Bookings | $\ge 99.95\%$ | 28 days | 21.9 minutes/month error budget |
| **Inventory Invariant** | **Oversell Rate** | **Strictly 0** | All time | Hard invariant: physical berths $\ge$ confirmed bookings |
| **Financial Invariant** | **Payment/Ticket Divergence**| **Strictly 0** | $> 15\text{ min}$ | Hard invariant: auto-reconciled within 15 minutes |
| **Tatkal Honest Rejection** | Latency ($p99$) | $< 50\text{ ms}$ | Peak Window| Instant edge reject for exhausted counters (F1 fix) |
| **Fairness Parity** | Latency Win-Rate Skew | $< 1.2\times$ | Burst Window| Ratio between $p10$ and $p90$ RTT cohorts |

### Error Budget Policy
- **$> 50\%$ Budget Consumed in 28-Day Window**: Non-critical feature releases frozen. Engineering priorities shift to performance tuning and test validation.
- **$> 100\%$ Budget Consumed**: Platform-wide deployment freeze. All engineering effort diverted to availability remediation and SRE root cause analysis.
- **Tatkal Operational Freeze**: Daily deployment freeze between **09:00:00 and 12:00:00 IST**.
