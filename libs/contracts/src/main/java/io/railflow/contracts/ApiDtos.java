package io.railflow.contracts;

import java.io.Serializable;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public class ApiDtos {

    public record StationDto(
        String stationCode,
        String name,
        String state,
        double latitude,
        double longitude
    ) implements Serializable {}

    public record TrainSearchResultDto(
        String trainNumber,
        String trainName,
        String fromStation,
        String toStation,
        String departureTime,
        String arrivalTime,
        String duration,
        List<ClassAvailabilityDto> availability
    ) implements Serializable {}

    public record ClassAvailabilityDto(
        String classCode,
        String quotaCode,
        String status,
        int availableCount,
        double fare,
        String confidence // authoritative, projected, degraded
    ) implements Serializable {}

    public record PassengerInputDto(
        String name,
        int age,
        String gender,
        String berthPreference
    ) implements Serializable {}

    public record CreateIntentRequest(
        String trainNumber,
        String journeyDate,
        String classCode,
        String quotaCode,
        String fromStation,
        String toStation,
        List<PassengerInputDto> passengers
    ) implements Serializable {}

    public record IntentTokenResponse(
        String intentId,
        String intentToken,
        Instant expiresAt,
        String signature
    ) implements Serializable {}

    public record BookingSubmitRequest(
        String intentToken,
        String trainNumber,
        String journeyDate,
        String classCode,
        String quotaCode,
        String fromStation,
        String toStation,
        List<PassengerInputDto> passengers
    ) implements Serializable {}

    public record BookingHoldResponse(
        UUID bookingId,
        String status,
        Instant expiresAt,
        double totalFare,
        String paymentIntentSecret
    ) implements Serializable {}

    public record VirtualQueueResponse(
        String queueId,
        int position,
        double estimatedWaitSeconds,
        String sseStreamUrl
    ) implements Serializable {}

    public record ConfirmedTicketResponse(
        UUID bookingId,
        String pnr,
        String trainNumber,
        String journeyDate,
        String status,
        List<AllocatedPassengerDto> passengers
    ) implements Serializable {}

    public record AllocatedPassengerDto(
        UUID passengerId,
        String name,
        String coach,
        int berthNumber,
        String berthType,
        String currentStatus
    ) implements Serializable {}

    public record PnrStatusResponse(
        String pnr,
        String trainNumber,
        String trainName,
        String journeyDate,
        String fromStation,
        String toStation,
        String bookingStatus,
        String chartStatus,
        List<AllocatedPassengerDto> passengers
    ) implements Serializable {}
}
