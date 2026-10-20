# ADR-0003: Edge Admission Control via Atomic Token Decrement (DECRBY)

- **Status**: Accepted
- **Deciders**: Principal Distributed Systems Architect

## Context
During the 10:00:00 Tatkal burst, 1,000,000 requests arrive in 2,000ms competing for 250,000 berths (average 100 berths/train). Letting 2,000,000 requests hit backend actors causes massive queue blowups.

## Options Considered
1. **Unbounded Pass-Through with Backend Queueing**: Triggers massive thread pool starvation and memory thrashing.
2. **Simple Rate Limiting (Token Bucket per IP)**: Ineffective against distributed botnets with thousands of residential IPs.
3. **Atomic Quota-Bounded Decrement in Redis Edge Cluster**: Seed Redis counters with $\text{quota\_size} \times 4$ (overshoot factor accounting for payment abandonment). Gateway decrements atomically.

## Decision
API Gateway executes atomic `DECRBY tatkal:{train}:{date}:{class}:{quota}`. If the counter falls below zero, the request is immediately rejected with `409 SOLD_OUT` and recommended alternative dates/trains.

## Consequences
- **Positive**: Sheds $99.9\%$ of unserviceable Tatkal write traffic at the edge in $< 5\text{ ms}$. Backend inventory actors receive a civilized, steady stream of valid candidates.
- **Negative**: Relies on Redis availability.
- **Mitigation**: If Redis cluster is unreachable, gateway degrades gracefully by falling back to conservative local rate-limiting counters.

## How We'd Know We Were Wrong
If payment abandonment rate exceeded $400\%$, causing berths to go unsold despite pending demand. The overshoot factor is dynamically tunable per route.
