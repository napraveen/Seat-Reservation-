package io.railflow.inventory.core;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public sealed interface InventoryCommand {

    long offset();
    Instant issuedAt();
    String trainNumber();
    String journeyDate();

    record HoldBerthsCommand(
        long offset,
        Instant issuedAt,
        String trainNumber,
        String journeyDate,
        UUID bookingId,
        String classCode,
        String quotaCode,
        int fromIdx,
        int toIdx,
        List<PassengerRequest> passengers,
        long holdTtlSeconds
    ) implements InventoryCommand {}

    record ConfirmBerthsCommand(
        long offset,
        Instant issuedAt,
        String trainNumber,
        String journeyDate,
        UUID bookingId
    ) implements InventoryCommand {}

    record ReleaseBerthsCommand(
        long offset,
        Instant issuedAt,
        String trainNumber,
        String journeyDate,
        UUID bookingId,
        String reason
    ) implements InventoryCommand {}

    record CancelBerthsCommand(
        long offset,
        Instant issuedAt,
        String trainNumber,
        String journeyDate,
        UUID bookingId,
        List<UUID> passengerIds
    ) implements InventoryCommand {}

    record PrepareChartCommand(
        long offset,
        Instant issuedAt,
        String trainNumber,
        String journeyDate,
        String chartMilestone // "FIRST_CHART_T4" or "FINAL_CHART_T30M"
    ) implements InventoryCommand {}
}
