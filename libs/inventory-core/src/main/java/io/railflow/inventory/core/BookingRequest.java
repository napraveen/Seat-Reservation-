package io.railflow.inventory.core;

import java.util.List;
import java.util.UUID;

public record BookingRequest(
    UUID bookingId,
    String trainNumber,
    int fromIdx,
    int toIdx,
    String classCode,
    String quotaCode,
    List<PassengerRequest> passengers,
    boolean autoUpgradationOptIn
) {
    public SegmentMask segmentMask() {
        return SegmentMask.of(fromIdx, toIdx);
    }
}
