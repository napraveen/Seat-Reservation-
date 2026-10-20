package io.railflow.contracts;

import java.io.Serializable;
import java.time.Instant;

/**
 * Canonical event envelope for all RailFlow topics.
 */
public record EventEnvelope<T>(
    String eventId,
    String eventType,
    Instant occurredAt,
    String producer,
    String traceparent,
    String partitionKey,
    String idempotencyKey,
    int schemaVersion,
    String causationId,
    String correlationId,
    T payload
) implements Serializable {}
