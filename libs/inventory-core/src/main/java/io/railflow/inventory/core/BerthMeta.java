package io.railflow.inventory.core;

/**
 * Metadata describing a physical coach berth.
 */
public record BerthMeta(
    int berthIndex,
    String coachId,
    int berthNumber,
    BerthType berthType,
    int bayNumber,
    boolean isSeniorFriendly,
    boolean isDivyangjanAccessible,
    boolean isLadiesCoach
) {
    public static BerthMeta of(int idx, String coach, int number, BerthType type, int bay) {
        boolean seniorFriendly = (type == BerthType.LOWER || type == BerthType.SIDE_LOWER);
        boolean divyangjanAccessible = (seniorFriendly && number <= 8);
        return new BerthMeta(idx, coach, number, type, bay, seniorFriendly, divyangjanAccessible, false);
    }
}
