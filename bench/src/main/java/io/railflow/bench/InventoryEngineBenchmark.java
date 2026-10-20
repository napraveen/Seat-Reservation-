package io.railflow.bench;

import io.railflow.inventory.core.*;
import org.openjdk.jmh.annotations.*;

import java.util.concurrent.TimeUnit;

@State(Scope.Benchmark)
@BenchmarkMode({Mode.Throughput, Mode.AverageTime})
@OutputTimeUnit(TimeUnit.MICROSECONDS)
public class InventoryEngineBenchmark {

    private BerthMajorInventoryEngine layoutA;
    private SegmentMajorInventoryEngine layoutB;
    private SegmentMask fullRouteMask;
    private SegmentMask shortSpanMask;
    private AllocationScorer scorer;
    private PassengerRequest pax;

    @Setup(Level.Trial)
    public void setup() {
        int totalSegments = 16;
        int totalBerths = 1200; // Typical 18-20 coach train

        BerthMeta[] metadata = new BerthMeta[totalBerths];
        for (int i = 0; i < totalBerths; i++) {
            metadata[i] = BerthMeta.of(i, "B" + (i / 72 + 1), (i % 72) + 1, BerthType.LOWER, i / 8);
        }

        layoutA = new BerthMajorInventoryEngine(totalSegments, metadata);
        layoutB = new SegmentMajorInventoryEngine(totalSegments, metadata);
        fullRouteMask = SegmentMask.of(0, 16);
        shortSpanMask = SegmentMask.of(2, 6);
        scorer = new AllocationScorer();
        pax = PassengerRequest.of("BenchPax", 35, "M", BerthType.LOWER);

        // Pre-fill ~60% occupancy
        for (int i = 0; i < totalBerths * 0.6; i++) {
            layoutA.allocateSpecific(i, SegmentMask.of(i % 5, (i % 5) + 3));
            layoutB.allocateSpecific(i, SegmentMask.of(i % 5, (i % 5) + 3));
        }
    }

    @Benchmark
    public int benchmarkLayoutA_ExactCheck() {
        return layoutA.countAvailableBerths(shortSpanMask);
    }

    @Benchmark
    public int benchmarkLayoutB_ExactCheck() {
        return layoutB.countAvailableBerths(shortSpanMask);
    }

    @Benchmark
    public int benchmarkLayoutA_BestFitAllocation() {
        int idx = layoutA.allocateBestFit(shortSpanMask, pax, null, scorer);
        if (idx >= 0) {
            layoutA.release(idx, shortSpanMask); // reset
        }
        return idx;
    }
}
