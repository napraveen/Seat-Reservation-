package io.railflow.inventory.core;

import java.util.List;

public interface InventoryEngine {

    int countAvailableBerths(SegmentMask mask);

    boolean canFitAny(SegmentMask mask);

    int allocateBestFit(
        SegmentMask requestMask,
        PassengerRequest passenger,
        List<BerthMeta> partyAllocated,
        AllocationScorer scorer
    );

    boolean allocateSpecific(int berthIdx, SegmentMask mask);

    void release(int berthIdx, SegmentMask mask);

    long getOccupancy(int berthIdx);

    int totalBerths();

    BerthMeta getMeta(int berthIdx);
}
