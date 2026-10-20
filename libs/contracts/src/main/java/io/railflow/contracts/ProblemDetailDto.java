package io.railflow.contracts;

import java.io.Serializable;
import java.util.List;

/**
 * RFC 9457 Problem Details error payload.
 */
public record ProblemDetailDto(
    String type,
    String title,
    int status,
    String code,
    String detail,
    String traceId,
    Long retryAfterMs,
    List<String> alternatives
) implements Serializable {

    public static ProblemDetailDto soldOut(String detail, String traceId, List<String> alternatives) {
        return new ProblemDetailDto(
            "https://railflow.io/errors/sold-out",
            "Berths Sold Out",
            409,
            "SOLD_OUT",
            detail,
            traceId,
            null,
            alternatives
        );
    }

    public static ProblemDetailDto raceLost(String detail, String traceId, List<String> alternatives) {
        return new ProblemDetailDto(
            "https://railflow.io/errors/berth-race-lost",
            "Berth Allocation Race Lost",
            409,
            "BERTH_RACE_LOST",
            detail,
            traceId,
            null,
            alternatives
        );
    }

    public static ProblemDetailDto rateLimited(String detail, String traceId, long retryAfterMs) {
        return new ProblemDetailDto(
            "https://railflow.io/errors/rate-limited",
            "Too Many Requests",
            429,
            "RATE_LIMITED",
            detail,
            traceId,
            retryAfterMs,
            null
        );
    }
}
