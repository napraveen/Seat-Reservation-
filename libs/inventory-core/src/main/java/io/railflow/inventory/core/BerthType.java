package io.railflow.inventory.core;

public enum BerthType {
    LOWER("LB", "Lower Berth"),
    MIDDLE("MB", "Middle Berth"),
    UPPER("UB", "Upper Berth"),
    SIDE_LOWER("SL", "Side Lower Berth"),
    SIDE_UPPER("SU", "Side Upper Berth"),
    WINDOW_SEAT("WS", "Window Seat"),
    AISLE_SEAT("AS", "Aisle Seat"),
    CABIN_LOWER("CL", "Cabin Lower"),
    CABIN_UPPER("CU", "Cabin Upper");

    private final String code;
    private final String description;

    BerthType(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public String code() {
        return code;
    }

    public String description() {
        return description;
    }

    public static BerthType fromCode(String code) {
        for (BerthType type : values()) {
            if (type.code.equalsIgnoreCase(code)) {
                return type;
            }
        }
        return LOWER;
    }
}
