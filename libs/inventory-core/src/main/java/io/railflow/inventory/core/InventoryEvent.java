package io.railflow.inventory.core;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public sealed interface InventoryEvent {

    long offset();
    Instant occurredAt();
    String trainNumber();
    String journeyDate();

    record BerthHeldEvent(
        long offset,
        Instant occurredAt,
        String trainNumber,
        String journeyDate,
        UUID bookingId,
        String classCode,
        String quotaCode,
        int fromIdx,
        int toIdx,
        List<HeldPassengerAllocation> allocations,
        Instant expiresAt
    ) implements InventoryEvent {}

    record HeldPassengerAllocation(
        UUID passengerId,
        String coachId,
        int berthNumber,
        BerthType berthType,
        int bayNumber,
        long segmentMask,
        String status // CNF, RAC, WL
    ) {}

    record BerthConfirmedEvent(
        long offset,
        Instant occurredAt,
        String trainNumber,
        String journeyDate,
        UUID bookingId
    ) implements InventoryEvent {}

    record BerthReleasedEvent(
        long offset,
        Instant occurredAt,
        String trainNumber,
        String journeyDate,
        UUID bookingId,
        String reason
    ) implements InventoryEvent {}

    record NoAccommodationEvent(
        long offset,
        Instant occurredAt,
        String trainNumber,
        String journeyDate,
        UUID bookingId,
        String reason
    ) implements InventoryEvent {}

    record ChartPreparedEvent(
        long offset,
        Instant occurredAt,
        String trainNumber,
        String journeyDate,
        String chartMilestone,
        int promotedRacCount,
        int promotedWlCount,
        int cancelledWlCount,
        int releasedBerthCount
    ) implements InventoryEvent {}
}
