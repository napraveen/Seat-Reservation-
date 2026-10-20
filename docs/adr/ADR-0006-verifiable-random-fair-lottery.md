# ADR-0006: Verifiable Random Fair Lottery During Tatkal Burst

- **Status**: Accepted
- **Deciders**: Principal Distributed Systems Architect

## Context
Under First-Come, First-Served (FCFS), touts with low network RTT (fiber lines near data centers) and automated browser scripts beat human travelers 9 times out of 10. Administrative policies (captcha, OTP) only partially mitigate this adversarial market.

## Options Considered
1. **Pure FCFS**: Rewards millisecond network edge; touts dominate.
2. **Strict Queueing by Arrival Timestamp**: Network latency still determines the timestamp order.
3. **Pre-Committed Verifiable Random Fair Lottery (VRF)**: Collect all valid intents in $[T, T+2000\text{ms}]$ and shuffle using a committed seed revealed post-window.

## Decision
All booking intents arriving within the first 2,000ms of the Tatkal window are batched and sorted deterministically using a Verifiable Random Function (VRF) seeded with a pre-committed hash $H(\text{seed})$. FCFS takes over after the initial 2,000ms window closes.

## Consequences
- **Positive**: A 40ms network advantage is rendered worthless. Win probability across network RTT cohorts is statistically flat ($< 1.2\times$ skew).
- **Negative**: Adds 2.0 seconds of latency to the earliest possible booking confirmation.
- **Mitigation**: Users are kept informed with real-time SSE queue positions and countdowns. 2 seconds is trivial compared to the 60–120s spinner experienced today.

## How We'd Know We Were Wrong
If user feedback indicated strong dissatisfaction with waiting 2 seconds for lottery resolution compared to instant FCFS rejection.
