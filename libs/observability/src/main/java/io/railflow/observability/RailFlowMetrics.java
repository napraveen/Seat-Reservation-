package io.railflow.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public final class RailFlowMetrics {

    private final MeterRegistry registry;

    // Hard Invariants
    private final Counter oversellTotal;
    private final AtomicInteger paymentTicketDivergence;

    // Operational Metrics
    private final Counter bookingsTotalCnf;
    private final Counter bookingsTotalSoldOut;
    private final Counter bookingsTotalRac;
    private final Counter bookingsTotalWl;
    private final AtomicLong admissionShedRatio;
    private final AtomicInteger degradationLevel;
    private final Timer allocationTimer;

    public RailFlowMetrics(MeterRegistry registry) {
        this.registry = registry;

        this.oversellTotal = Counter.builder("railflow_oversell_total")
            .description("Total count of oversold berths. Must be strictly 0 all time.")
            .register(registry);

        this.paymentTicketDivergence = new AtomicInteger(0);
        Gauge.builder("railflow_payment_ticket_divergence", paymentTicketDivergence, AtomicInteger::get)
            .description("Unreconciled payment-ticket count older than 15 minutes. Must be 0.")
            .register(registry);

        this.bookingsTotalCnf = Counter.builder("railflow_bookings_total")
            .tag("outcome", "CNF")
            .register(registry);

        this.bookingsTotalSoldOut = Counter.builder("railflow_bookings_total")
            .tag("outcome", "SOLD_OUT")
            .register(registry);

        this.bookingsTotalRac = Counter.builder("railflow_bookings_total")
            .tag("outcome", "RAC")
            .register(registry);

        this.bookingsTotalWl = Counter.builder("railflow_bookings_total")
            .tag("outcome", "WL")
            .register(registry);

        this.admissionShedRatio = new AtomicLong(0);
        Gauge.builder("railflow_admission_shed_ratio", admissionShedRatio, val -> val.get() / 1000.0)
            .register(registry);

        this.degradationLevel = new AtomicInteger(0);
        Gauge.builder("railflow_degradation_level", degradationLevel, AtomicInteger::get)
            .register(registry);

        this.allocationTimer = Timer.builder("railflow_allocation_duration_microseconds")
            .publishPercentileHistogram()
            .register(registry);
    }

    public void recordOversell() { oversellTotal.increment(); }
    public void setPaymentDivergence(int count) { paymentTicketDivergence.set(count); }
    public void recordBookingOutcome(String outcome) {
        switch (outcome) {
            case "CNF" -> bookingsTotalCnf.increment();
            case "RAC" -> bookingsTotalRac.increment();
            case "WL" -> bookingsTotalWl.increment();
            case "SOLD_OUT" -> bookingsTotalSoldOut.increment();
        }
    }
    public void setDegradationLevel(int level) { degradationLevel.set(level); }
    public Timer allocationTimer() { return allocationTimer; }
}
