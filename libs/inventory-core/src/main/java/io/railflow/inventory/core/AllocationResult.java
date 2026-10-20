package io.railflow.inventory.core;

import java.util.List;

public sealed interface AllocationResult {

    record ConfirmedAllocation(
        String coachId,
        int berthNumber,
        BerthType berthType,
        int bayNumber,
        SegmentMask allocatedSegments
    ) implements AllocationResult {}

    record RacAllocation(
        int racNumber,
        String coachId,
        int berthNumber
    ) implements AllocationResult {}

    record WaitlistAllocation(
        String waitlistType, // GNWL, RLWL, PQWL, TQWL, RSWL
        int waitlistNumber
    ) implements AllocationResult {}

    record NoAccommodation(
        String reason
    ) implements AllocationResult {}

    record BatchAllocationResult(
        boolean allConfirmed,
        List<AllocationResult> passengerOutcomes
    ) implements AllocationResult {}
}
