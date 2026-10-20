# 06. API Contracts, Error Taxonomy & Idempotency Rules

---

## 1. Design Standards

- **OpenAPI 3.1 Spec-First**: Master specification in [`libs/contracts/openapi/railflow-api.yaml`](file:///f:/2026%20-%20Projects/Railway%20Seat%20Reservation/libs/contracts/openapi/railflow-api.yaml).
- **RFC 9457 Problem Details**: All error responses return `application/problem+json` with machine-readable error codes and correlation IDs.
- **Header Standards**:
  - `X-Request-Id`: Unique request identifier passed from edge to database.
  - `traceparent`: W3C distributed tracing context header.
  - `Idempotency-Key`: Required on all mutating endpoints (`POST /api/v1/bookings`, `POST /api/v1/payments/intent`, etc.).
  - `as_of` & `staleness_ms`: Returned on all cached or projected read responses.

---

## 2. Comprehensive Error Taxonomy

| HTTP Status | Error Code (`code`) | Trigger Condition | Recommended Client Action |
|---|---|---|---|
| `409 Conflict` | `SOLD_OUT` | Edge admission counter exhausted | Render sold-out state instantly; display alternatives. Do not retry. |
| `409 Conflict` | `BERTH_RACE_LOST` | Admitted past edge, but quota ran out in actor | Offer RAC, Waitlist, or alternate train dates. |
| `202 Accepted` | `QUEUED` | Admitted into Tatkal 2000ms lottery window | Connect to SSE stream; display truthful queue position & ETA. |
| `422 Unprocessable` | `IDEMPOTENCY_KEY_REUSE`| Same key submitted with differing request body | Flag programming error to client developer. |
| `423 Locked` | `BOOKING_WINDOW_CLOSED` | Before Tatkal open (09:59:59) or post-chart | Render live countdown synced to authoritative server clock. |
| `429 Too Many Req`| `RATE_LIMITED` | Per-IP, per-user, or per-device token bucket cap | Respect `Retry-After` header with exponential backoff + jitter. |
| `403 Forbidden` | `IDENTITY_CAP_EXCEEDED`| Per-Aadhaar monthly ticket limit exceeded (max 12) | Inform user of quota ceiling. |
| `403 Forbidden` | `ATTESTATION_FAILED` | WebCrypto keypair or device signature invalid | Route user to step-up challenge / biometric prompt. |
| `503 Service Unavail`| `INVENTORY_UNAVAILABLE`| Partition actor rebalancing across Kafka nodes | Retry with backoff; honor `Retry-After: 2`. |
| `503 Service Unavail`| `DEGRADED_READ_ONLY` | System operating at Degradation Level L4 or L5 | Switch UI to read-only browsing mode with warning banner. |
| `402 Payment Req` | `PAYMENT_FAILED` | Mock PSP payment authorization declined | Retry payment; booking soft-hold remains active until `expires_at`. |
| `410 Gone` | `HOLD_EXPIRED` | Soft-hold TTL elapsed without confirmed payment | Release UI state; prompt user to initiate a new reservation. |

---

## 3. Idempotency Specification

### Semantics
1. **First Request**: Client submits mutating request with `Idempotency-Key: <UUID>`. Service executes business logic and stores `{idempotency_key, request_hash, http_status, response_body}` atomically in Redis (24-hour TTL) and PostgreSQL.
2. **Identical Replay**: Subsequent request with the same `Idempotency-Key` and **identical payload** immediately returns the cached status and body without re-executing inventory allocation or payment capture.
3. **Mismatched Replay**: Request with identical key but **different payload** is rejected with `422 Unprocessable Entity` (`code: IDEMPOTENCY_KEY_REUSE`).
