# 14. Architecture Trade-Offs: What We'd Do Differently at Scale

---

## 1. What Changes with 10 Staff Engineers and AWS/GCP Budget

| Dimension | Current Local / MVP Design | Production Scale Design (AWS / GCP) | Engineering Trade-Off |
|---|---|---|---|
| **Multi-Region Strategy** | Single-region, multi-AZ deployment | Active-Active Multi-Region with Partition Sharding by Railway Zone | Multi-region active-active is unnecessary within India since national network latency is $< 45\text{ ms}$. Sharding by Zonal Railway (Northern, Western, Southern) isolates blast radius. |
| **Command Log** | Redpanda single-cluster (Docker) | Managed Kafka (AWS MSK / Confluent Cloud) with Tiered Storage | Tiered storage allows infinite retention on S3 without managing EBS volume limits. |
| **Message Relaying** | Application polling outbox publisher | Debezium CDC + Kafka Connect directly reading PostgreSQL WAL | Eliminates polling overhead and sub-second outbox latency. |
| **Edge Compute** | Spring Cloud Gateway on Netty | Cloudflare Workers / AWS CloudFront with Edge KV for Admission Counters | Moves the 5ms admission counter `DECRBY` physically closer to the user ($< 1\text{ ms}$ at CDN PoP). |
| **Hardware Acceleration** | Software bitwise 64-bit integer scan | Java Vector API (`LongVector`) or AVX-512 SIMD native extension | Increases exact availability check throughput from ~850k to $> 3.5\text{M}$ checks/sec/core. |
| **Search Engine** | PostgreSQL Full-Text Trigrams | Managed OpenSearch Cluster with k-NN phonetic ranking | Scales autocomplete queries to $500,000\text{ RPS}$ with sub-millisecond phonetic caching. |

---

## 2. Hard Choices Defended

### Trade-Off 1: Single-Writer Actor vs. Distributed Locking (Redis / ZooKeeper)
- *Decision*: Route all writes for `(train_id, journey_date)` through a single thread via Kafka partition ordering.
- *Downside*: If an actor thread pauses or crashes, all bookings for that specific train stall for up to 1.5 seconds during rebalance.
- *Defense*: Distributed locks across thousands of concurrent clients inevitably lead to deadlocks, split-brain lock expirations, and lock-acquisition timeouts under Tatkal congestion. The single-writer actor eliminates concurrency bugs by construction.

### Trade-Off 2: Bounded-Staleness CQRS vs. Strongly Consistent Reads
- *Decision*: Availability queries read from Caffeine and Redis read models updated asynchronously ($\approx 150\text{ ms}$ lag).
- *Downside*: A passenger may see `AVAILABLE 4` on the search screen, click book, and find berths exhausted.
- *Defense*: Serving 500,000 reads/sec strongly consistent from the write engine would throttle the write path. RailFlow mitigates this by returning an honest `as_of` timestamp on the UI and validating exact berths at hold initiation.
