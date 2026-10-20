package io.railflow.inventory.core;

import java.util.UUID;

public record PassengerRequest(
    UUID passengerId,
    String name,
    int age,
    String gender,
    BerthType preference,
    boolean isSeniorCitizen,
    boolean isDivyangjan,
    boolean isLadiesQuotaOptIn
) {
    public static PassengerRequest of(String name, int age, String gender, BerthType preference) {
        boolean senior = age >= 60;
        boolean ladies = "F".equalsIgnoreCase(gender);
        return new PassengerRequest(UUID.randomUUID(), name, age, gender, preference, senior, false, ladies);
    }
}
