package io.railflow.inventory.core;

import java.util.*;

public final class QuotaPool {

    private final String classCode;
    private final String quotaCode;
    private final InventoryEngine engine;
    private final int racCapacity;
    private final int waitlistCapacity;
    private int currentRacCount;
    private int currentWaitlistCount;

    public QuotaPool(String classCode, String quotaCode, InventoryEngine engine, int racCapacity, int waitlistCapacity) {
        this.classCode = classCode;
        this.quotaCode = quotaCode;
        this.engine = engine;
        this.racCapacity = racCapacity;
        this.waitlistCapacity = waitlistCapacity;
        this.currentRacCount = 0;
        this.currentWaitlistCount = 0;
    }

    public String classCode() { return classCode; }
    public String quotaCode() { return quotaCode; }
    public InventoryEngine engine() { return engine; }
    public int racCapacity() { return racCapacity; }
    public int waitlistCapacity() { return waitlistCapacity; }
    public int currentRacCount() { return currentRacCount; }
    public int currentWaitlistCount() { return currentWaitlistCount; }

    public int incrementRac() {
        return ++currentRacCount;
    }

    public int incrementWaitlist() {
        return ++currentWaitlistCount;
    }

    public void decrementRac() {
        if (currentRacCount > 0) currentRacCount--;
    }

    public void decrementWaitlist() {
        if (currentWaitlistCount > 0) currentWaitlistCount--;
    }

    public boolean hasRacCapacity() {
        return currentRacCount < racCapacity;
    }

    public boolean hasWaitlistCapacity() {
        return currentWaitlistCount < waitlistCapacity;
    }
}
