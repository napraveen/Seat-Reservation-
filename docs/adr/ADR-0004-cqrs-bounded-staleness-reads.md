# ADR-0004: CQRS Read Model with Explicit Bounded-Staleness Contract

- **Status**: Accepted
- **Deciders**: Principal Distributed Systems Architect

## Context
Enquiry queries (search, availability calendar, PNR status) outnumber booking mutations by 100:1, peaking at $500,000\text{ RPS}$. Direct reads against the single-writer inventory actor would degrade write performance.

## Options Considered
1. **Strong Consistency for Reads**: Read directly from the inventory actor. Fails to scale beyond a few thousand RPS.
2. **Unbounded Eventual Consistency**: Cache responses with arbitrary TTLs. Results in user frustration (displaying AVAILABLE-40 when seats are sold out).
3. **CQRS with Bounded-Staleness Contract**: Asynchronously project inventory deltas into Redis L2 and Caffeine L1. Every read carries explicit metadata (`as_of`, `staleness_ms`).

## Decision
Adopt CQRS with an explicit bounded-staleness SLA ($p99 < 250\text{ ms}$, hard cap $1,000\text{ ms}$). If projection lag exceeds $1,000\text{ ms}$, the response header indicates `availability_confidence: degraded`, and the client UI displays an honest freshness warning.

## Consequences
- **Positive**: 500,000 reads/sec served with sub-millisecond latency from multi-layer memory caches. Write engine remains unhindered.
- **Negative**: Read responses are slightly decoupled from instantaneous microsecond state.
- **Mitigation**: Authoritative write paths perform an exact bitmask scan at hold initiation, guaranteeing that outdated read data never causes an oversell.

## How We'd Know We Were Wrong
If user abandon rate increased dramatically due to seeing "degraded availability" warnings during normal non-peak hours.
