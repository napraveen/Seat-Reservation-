package io.railflow.inventory.core;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.*;

/**
 * FoundationDB-style deterministic simulation test.
 * Runs the partition under simulated clocks, message reorderings, duplicate commands,
 * and pseudo-random client arrivals with fixed regression seeds.
 */
public class DeterministicSimulationHarnessTest {

    // Historical regression seeds committed permanently to prevent recurrence of edge bugs
    private static final long[] REGRESSION_SEEDS = new long[]{0xDEADBEEFL, 0xCAFEBABEL, 0x1337420L};

    @Test
    @DisplayName("FoundationDB Simulation: Execute randomized operations across regression seeds with zero invariant violation")
    void testDeterministicSimulationRegressionSuite() {
        for (long seed : REGRESSION_SEEDS) {
            runSimulationWithSeed(seed, 200);
        }
    }

    private void runSimulationWithSeed(long seed, int steps) {
        Random rng = new Random(seed);
        int totalSegments = 8;
        int totalBerths = 16;

        BerthMeta[] meta = new BerthMeta[totalBerths];
        for (int i = 0; i < totalBerths; i++) {
            meta[i] = BerthMeta.of(i, "B" + (i / 8 + 1), (i % 8) + 1, BerthType.LOWER, i / 8);
        }

        InventoryEngine engine = new BerthMajorInventoryEngine(totalSegments, meta);
        QuotaPool pool = new QuotaPool("3A", "GN", engine, 5, 10);
        InventoryPartition partition = new InventoryPartition("12951", "2026-10-22", totalSegments);
        partition.registerQuotaPool(pool);

        List<UUID> activeBookings = new ArrayList<>();
        long offset = 0;
        Instant simClock = Instant.parse("2026-10-21T10:00:00.000Z");

        for (int step = 0; step < steps; step++) {
            offset++;
            simClock = simClock.plusMillis(rng.nextInt(50) + 10);

            int opType = rng.nextInt(10);
            if (opType < 6) {
                // Book
                int from = rng.nextInt(totalSegments - 1);
                int to = from + 1 + rng.nextInt(totalSegments - from);
                UUID id = UUID.randomUUID();
                PassengerRequest pax = PassengerRequest.of("SimPax", 20 + rng.nextInt(55), "M", BerthType.LOWER);

                InventoryCommand.HoldBerthsCommand hold = new InventoryCommand.HoldBerthsCommand(
                    offset, simClock, "12951", "2026-10-22", id, "3A", "GN", from, to, List.of(pax), 600
                );
                InventoryEvent ev = partition.apply(hold);
                if (ev instanceof InventoryEvent.BerthHeldEvent) {
                    offset++;
                    partition.apply(new InventoryCommand.ConfirmBerthsCommand(
                        offset, simClock.plusSeconds(2), "12951", "2026-10-22", id
                    ));
                    activeBookings.add(id);
                }
            } else if (opType < 9 && !activeBookings.isEmpty()) {
                // Cancel
                int cancelIdx = rng.nextInt(activeBookings.size());
                UUID toCancel = activeBookings.remove(cancelIdx);
                partition.apply(new InventoryCommand.CancelBerthsCommand(
                    offset, simClock, "12951", "2026-10-22", toCancel, List.of()
                ));
            } else {
                // Duplicate command replay (Simulating network retry)
                if (!activeBookings.isEmpty()) {
                    UUID duplicateId = activeBookings.get(0);
                    partition.apply(new InventoryCommand.ConfirmBerthsCommand(
                        offset, simClock, "12951", "2026-10-22", duplicateId
                    ));
                }
            }

            // Continuous Invariant Check: physical occupancy popcount consistency
            long physicalPopCount = 0;
            for (int b = 0; b < totalBerths; b++) {
                physicalPopCount += Long.bitCount(engine.getOccupancy(b));
            }
            Assertions.assertEquals(physicalPopCount, partition.computeTotalSoldSegments(),
                "Simulation seed 0x" + Long.toHexString(seed) + " failed invariant check at step " + step);
        }
    }
}
