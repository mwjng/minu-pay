package com.minupay.payment.infrastructure.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
public class PaymentMetrics {

    private final Counter approvedCounter;
    private final Counter failedCounter;
    private final Counter cancelledCounter;
    private final Timer pgApproveTimer;

    public PaymentMetrics(MeterRegistry registry) {
        this.approvedCounter = outcomeCounter(registry, "APPROVED");
        this.failedCounter = outcomeCounter(registry, "FAILED");
        this.cancelledCounter = outcomeCounter(registry, "CANCELLED");
        this.pgApproveTimer = Timer.builder("minupay.pg.approve")
                .description("PG approve call duration")
                .register(registry);
    }

    public void recordApproved() {
        approvedCounter.increment();
    }

    public void recordFailed() {
        failedCounter.increment();
    }

    public void recordCancelled() {
        cancelledCounter.increment();
    }

    public void recordPgApproveDuration(long durationNanos) {
        pgApproveTimer.record(durationNanos, TimeUnit.NANOSECONDS);
    }

    private static Counter outcomeCounter(MeterRegistry registry, String status) {
        return Counter.builder("minupay.payment.requests")
                .description("Payment request outcomes")
                .tag("status", status)
                .register(registry);
    }
}
