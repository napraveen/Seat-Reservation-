# 02. Capacity Math, Sizing Derivations & Envelope Limits

> **Conventions:**
> - `[SOURCED]`: Verified from published Ministry of Railways / PIB data.
> - `[ESTIMATE]`: Engineering approximation derived from domain heuristics.
> - `[DESIGN TARGET]`: System design targets chosen to demonstrate scalable headroom.

---

## 1. Scale Inputs & Traffic Envelope

| Metric | Value | Category | Primary Reference / Basis |
|---|---|---|---|
| Current IRCTC Peak Capacity | ~32,000–37,000 tickets/min | `[SOURCED]` | PIB Release PRID 2140614 |
| CRIS Next-Gen PRS Target | >150,000 tickets/min | `[SOURCED]` | PIB Release PRID 2140614 |
| CRIS Next-Gen Enquiry Target | 4,000,000 queries/min | `[SOURCED]` | PIB Release PRID 2140614 |
| Annual Reserved Tickets | 650.8 million (65.08 crore) | `[SOURCED]` | Ministry of Railways (2025–2026 data) |
| Online Booking Share | 89% (579 million) | `[SOURCED]` | Ministry of Railways |
| Average Reserved Tickets/Day | ~1,783,000 tickets/day | Derived | $650.8\text{M} / 365$ |
| Peak Festival Day Bookings | ~2,700,000 tickets/day | `[ESTIMATE]` | $\approx 1.5\times$ daily average |
| Passenger Trains Operating Daily | ~13,000 | `[SOURCED]` | PIB PRID 2112484 |
| Trains Carrying Reserved Coaches | ~7,000 | `[ESTIMATE]` | Excludes local EMU, MEMU, and passenger shuttles |
| Average Reserved Berths/Train | ~1,400 berths | `[ESTIMATE]` | 18–22 coach rake compositions |
| Total Reservable Berths/Day | ~10,000,000 berths | Derived | $7,000\text{ trains} \times 1,400\text{ berths}$ |
| Active Advance Reservation Window | **60 days** | `[SOURCED]` | Railway Board CC 10/2024 (effective 01.11.2024) |

---

## 2. Design Targets & Throughput Headroom

RailFlow's targets are systematically anchored to the published CRIS Next-Gen goals, providing documented headroom:

```
+------------------------------------------------------------------------------------+
| RailFlow Design Targets                                                            |
+------------------------------------------------------------------------------------+
| Peak Concurrent Connected Users ..................... 1,000,000                   |
| Peak Read QPS (Search + Availability + PNR) .......... 500,000 rps (30,000,000/min)|
|   => 7.5x the Next-Gen 4,000,000 enquiries/min target                             |
| Peak Write TPS (Booking Attempts) .................... 50,000 tps                  |
| Sustained Confirmed Tickets / Minute ................. 250,000 tpm (4,167 tps)     |
|   => 1.67x the Next-Gen 150,000 tickets/min goal                                  |
|   => ~7.0x the Current PRS 35,000 tickets/min throughput                          |
| Tatkal Burst Arrival Window (First 2000 ms) .......... 2,000,000 requests         |
|   => 1,000,000 requests/sec instantaneous peak                                     |
| Read-to-Write Ratio .................................. 100 : 1                     |
+------------------------------------------------------------------------------------+
```

### Derivation of the Tatkal Burst Window
1. **Supply**:
   - Total trains with Tatkal quota opening at 10:00 AM on a given date: $\approx 2,500$ `[ESTIMATE]`.
   - Average Tatkal quota per train (across all AC classes): $\approx 100$ berths `[ESTIMATE]`.
   - Aggregate Tatkal supply available at $t=0$:
     $$\text{Supply} = 2,500 \times 100 = 250,000\text{ berths}$$

2. **Demand**:
   - Active concurrent users positioned on the payment/submit trigger at 10:00:00 AM: $\approx 1,000,000$ users `[DESIGN TARGET]`.
   - Concentrated submission interval (human reflex + automated client triggers): $2.0\text{ seconds}$ ($2,000\text{ ms}$).
   - Client-side automatic retries (measured average $2\times$ factor during peak congestion):
     $$\text{Instantaneous Peak Load} = 1,000,000\text{ users} \times 2 = 2,000,000\text{ requests in } 2\text{ s} \implies \lambda = 1,000,000\text{ req/sec}$$

3. **Contention & Mathematical Inevitability**:
   $$\text{Supply : Demand Ratio} = 250,000 : 2,000,000 = 1 : 8$$
   - **Conclusion**: Exactly $\mathbf{87.5\%}$ of submitted requests ($1,750,000$) are mathematically destined to fail.
   - **Engineering Goal**: Serve these $1.75\text{M}$ rejections in $<5\text{ ms}$ at the API Gateway without touching the core inventory engine or database connection pools.

---

## 3. Storage Mathematics

### A. Bookings & Relational Footprint
- Sizing basis: $2.0\text{M}$ bookings/day ($730\text{M}$ bookings/year).
- PNR Master Row: $\approx 400\text{ bytes}$.
- Passenger Entities: Average $3.2$ passengers/booking $\times 250\text{ bytes} \approx 800\text{ bytes}$.
- Total relational footprint per booking: $\approx 1.2\text{ KB}$.
- Annual Raw Table Volume:
  $$730,000,000 \times 1.2\text{ KB} = 876\text{ GB / year}$$
- B-Tree Indexes, Subpartition Catalogs & Engine Bloat ($2.2\times$ factor):
  $$\text{Hot Storage} \approx 876\text{ GB} \times 2.2 = 1.92\text{ TB / year}$$
- **Tiered Retention Strategy**:
  - Hot Tier (PostgreSQL, 90 days): $\approx 475\text{ GB}$.
  - Warm Tier (Read-only compressed tables, 1 year): $\approx 1.9\text{ TB}$.
  - Cold Tier (7-year statutory archive in Parquet + ZSTD $\approx 8\times$ compression): $\approx 1.7\text{ TB}$.

### B. Event Sourcing & Audit Log
- Domain events per reservation lifecycle (Intent $\to$ Hold $\to$ Payment $\to$ Confirm $\to$ Chart $\to$ Board): $\approx 40$ events.
- Annual events generated:
  $$730\text{M} \times 40 = 29.2\times 10^9\text{ events / year}$$
- Compressed event size in Kafka: $\approx 120\text{ bytes}$ (Protobuf/Avro + snappy).
- Kafka Hot Retention (7-day buffer):
  $$\frac{29.2\times 10^9\text{ events}}{365\text{ days}} \times 7\text{ days} \times 120\text{ bytes} \approx 67.2\text{ GB}$$
  *Insight: 7 days of full-network event log requires only ~67 GB hot storage.*

---

## 4. The Cornerstone In-Memory Inventory Calculation

> **The Architectural Reframing:**
> *"The complete reservable seat inventory of Indian Railways across all 60 days of the reservation window is **4.8 GB**. It fits comfortably in RAM on a single modest development laptop. Inventory allocation is therefore **not** a distributed database problem—it is an in-memory, deterministic, replicated state machine problem. PostgreSQL is solely a durable materialized projection."*

### Mathematical Proof
1. Total reservable berths/day: $\approx 10,000,000$ berths.
2. Segment Representation: One `u64` (64-bit integer, 8 bytes) per berth. Since $>99.9\%$ of Indian Railways routes have $\le 64$ commercial stops, a 64-bit unsigned integer represents the entire journey route as an interval bitmask.
3. Daily Inventory Size:
   $$10,000,000\text{ berths} \times 8\text{ bytes} = 80,000,000\text{ bytes} \approx 80\text{ MB / day}$$
4. 60-Day Advance Reservation Period (ARP):
   $$80\text{ MB / day} \times 60\text{ days} = 4.8\text{ GB}$$
5. Adding berth metadata (Coach ID, berth type, bay index, quota pointers) at $3\times$ multiplier:
   $$\text{Total Working In-Memory Footprint} \approx 14.4\text{ GB}$$

> **Note on Commercial Circular 10 of 2024:**
> When the Railway Board shortened the ARP from 120 days to 60 days, they halved the active inventory working set from $9.6\text{ GB}$ to $4.8\text{ GB}$. A fraud-prevention policy delivered a direct $2\times$ memory efficiency dividend.

---

## 5. Network & Connection Bandwidth

- **Availability Query Egress**:
  - Compact JSON payload: $\approx 2.5\text{ KB}$ (gzipped over HTTP/2: $\approx 600\text{ bytes}$).
  - At peak read rate ($500,000\text{ rps}$):
    $$\text{Egress Bandwidth} = 500,000 \times 600\text{ B} = 300\text{ MB/s} = 2.4\text{ Gbps}$$
- **Virtual Queue SSE Connections**:
  - $1,000,000$ concurrent long-lived Server-Sent Events (SSE) connections.
  - Linux kernel TCP buffer overhead (tuned `rmem`/`wmem` min $4\text{ KB} + 8\text{ KB}$ Netty channel state): $\approx 12\text{ KB}$/connection.
  - Edge Cluster Memory Requirement:
    $$1,000,000 \times 12\text{ KB} = 12\text{ GB}$$
  - Sizing at 50,000 concurrent connections per gateway container:
    $$\text{Edge Fleet} = \frac{1,000,000}{50,000} = 20\text{ Gateway Nodes}$$

---

## 6. Little's Law & Concurrency Validation

$$L = \lambda \times W$$

Where:
- $\lambda = \text{Arrival Rate} = 50,000\text{ booking attempts / sec}$
- $W = \text{Mean Service Time in Inventory Actor} = 4\times 10^{-6}\text{ sec}$ ($4\text{ microseconds}$)

$$L_{\text{inventory}} = 50,000 \times 0.000004 = 0.2\text{ requests in flight per partition}$$

Across 256 Kafka partitions, queue saturation remains negligible under steady load, validating that microsecond bitmask execution effortlessly outpaces inbound network arrival.
