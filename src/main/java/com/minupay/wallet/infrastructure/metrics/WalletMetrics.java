package com.minupay.wallet.infrastructure.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class WalletMetrics {

    private final Counter chargeCounter;
    private final DistributionSummary chargeAmount;

    public WalletMetrics(MeterRegistry registry) {
        this.chargeCounter = Counter.builder("minupay.wallet.charges")
                .description("Wallet charge invocations")
                .register(registry);
        this.chargeAmount = DistributionSummary.builder("minupay.wallet.charge.amount")
                .description("Wallet charge amount (krw)")
                .baseUnit("krw")
                .register(registry);
    }

    public void recordCharge(long amountKrw) {
        chargeCounter.increment();
        chargeAmount.record(amountKrw);
    }
}
