# ADR-0007: Java 21 Virtual Threads for Business Core; Reactive Only at Gateway

- **Status**: Accepted
- **Deciders**: Principal Distributed Systems Architect

## Context
Handling 1,000,000 concurrent user sessions and 50,000 transactions/second requires scalable thread management. Traditional thread-per-request models saturate OS thread memory. Full reactive (Reactor / WebFlux / R2DBC) across the entire application introduces severe debugging, stack trace, and cognitive overhead.

## Options Considered
1. **Traditional Platform Threads**: Saturates OS memory; 10,000 threads consume ~10 GB RAM just in thread stacks.
2. **Full Reactive Stack (WebFlux + R2DBC across all 12 services)**: High mental tax, complex stack traces, reactive boundary bugs, poor compatibility with mature Java libraries.
3. **Hybrid Architecture**: Reactive WebFlux on Netty strictly at the API Gateway (holding 1M idle SSE connections); Java 21 Virtual Threads (`Loom`) for the business microservices.

## Decision
Configure `spring.threads.virtual.enabled=true` across all business tier services (Spring MVC / Tomcat / JDBC). Use reactive WebFlux exclusively at the `api-gateway` to terminate 1,000,000 long-lived SSE virtual queue connections with minimal memory overhead.

## Consequences
- **Positive**: Clean, readable imperative code in business services. Straightforward debugging and stack traces. Gateway holds 50,000 idle connections per node with $< 500\text{ MB}$ RAM.
- **Negative**: Risk of carrier thread pinning on legacy `synchronized` blocks.
- **Mitigation**: Automated CI checks running `-Djdk.tracePinnedThreads=full` ensure all blocking locks use `ReentrantLock` instead of `synchronized`.

## How We'd Know We Were Wrong
If Loom virtual threads experienced persistent carrier thread pinning under production load, causing throughput collapse.
