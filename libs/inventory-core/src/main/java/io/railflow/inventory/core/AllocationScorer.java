package io.railflow.inventory.core;

import java.util.List;

/**
 * Multi-objective bin-packing allocation scoring function.
 * Treats railway berth allocation as a memory fragmentation avoidance problem.
 */
public final class AllocationScorer {

    public static final double DEFAULT_W_CONTIG = 12.0;
    public static final double DEFAULT_W_FRAG = 18.0;
    public static final double DEFAULT_W_PREF = 10.0;
    public static final double DEFAULT_W_GROUP = 15.0;
    public static final double DEFAULT_W_POLICY = 25.0;
    public static final double DEFAULT_W_VIRGIN = 20.0;

    private final double wContig;
    private final double wFrag;
    private final double wPref;
    private final double wGroup;
    private final double wPolicy;
    private final double wVirgin;

    public AllocationScorer() {
        this(DEFAULT_W_CONTIG, DEFAULT_W_FRAG, DEFAULT_W_PREF, DEFAULT_W_GROUP, DEFAULT_W_POLICY, DEFAULT_W_VIRGIN);
    }

    public AllocationScorer(double wContig, double wFrag, double wPref, double wGroup, double wPolicy, double wVirgin) {
        this.wContig = wContig;
        this.wFrag = wFrag;
        this.wPref = wPref;
        this.wGroup = wGroup;
        this.wPolicy = wPolicy;
        this.wVirgin = wVirgin;
    }

    public double score(
        long existingOccupancy,
        SegmentMask requestMask,
        BerthMeta berth,
        PassengerRequest passenger,
        List<BerthMeta> alreadyAllocatedInParty,
        int totalSegments
    ) {
        long reqWord = requestMask.asSingleWord();
        // Guard check: must fit
        if ((existingOccupancy & reqWord) != 0) {
            return Double.NEGATIVE_INFINITY;
        }

        double score = 0.0;

        // 1. Contiguity Bonus: does req sit flush against existing bookings?
        int reqFrom = requestMask.fromIdx();
        int reqTo = requestMask.toIdx();
        boolean leftFlush = (reqFrom > 0) && ((existingOccupancy & (1L << (reqFrom - 1))) != 0);
        boolean rightFlush = (reqTo < totalSegments) && ((existingOccupancy & (1L << reqTo)) != 0);
        int contiguity = (leftFlush ? 1 : 0) + (rightFlush ? 1 : 0);
        score += wContig * contiguity;

        // 2. Residual Hole Penalty: how many isolated gaps of 1 segment does this create?
        long newOccupancy = existingOccupancy | reqWord;
        int holes = countIsolatedHoles(newOccupancy, totalSegments);
        score -= wFrag * holes;

        // 3. Berth Preference Match
        if (passenger.preference() != null && passenger.preference() == berth.berthType()) {
            score += wPref;
        }

        // 4. Group Adjacency (same bay / same coach)
        if (alreadyAllocatedInParty != null && !alreadyAllocatedInParty.isEmpty()) {
            for (BerthMeta coPassengerBerth : alreadyAllocatedInParty) {
                if (coPassengerBerth.coachId().equals(berth.coachId())) {
                    score += (wGroup * 0.4);
                    if (coPassengerBerth.bayNumber() == berth.bayNumber()) {
                        score += (wGroup * 0.6); // same bay strong bonus
                    }
                }
            }
        }

        // 5. Policy Bonus: Senior citizens get Lower Berths; Divyangjan get accessible lower berths
        if (passenger.isSeniorCitizen() && berth.isSeniorFriendly()) {
            score += wPolicy;
        }
        if (passenger.isDivyangjan() && berth.isDivyangjanAccessible()) {
            score += (wPolicy * 1.5);
        }
        if (passenger.isLadiesQuotaOptIn() && berth.isLadiesCoach()) {
            score += wPolicy;
        }

        // 6. Virgin Berth Protection: protect completely empty berths against short-distance fragmenters
        boolean isVirgin = (existingOccupancy == 0L);
        if (isVirgin) {
            int span = requestMask.spanLength();
            if (span < totalSegments) {
                double spanWaste = (double) (totalSegments - span) / totalSegments;
                score -= wVirgin * spanWaste;
            }
        }

        return score;
    }

    private int countIsolatedHoles(long mask, int totalSegments) {
        int holes = 0;
        for (int i = 0; i < totalSegments; i++) {
            boolean isFree = ((mask & (1L << i)) == 0);
            if (isFree) {
                boolean leftOccupied = (i == 0) || ((mask & (1L << (i - 1))) != 0);
                boolean rightOccupied = (i == totalSegments - 1) || ((mask & (1L << (i + 1))) != 0);
                if (leftOccupied && rightOccupied) {
                    holes++;
                }
            }
        }
        return holes;
    }
}
