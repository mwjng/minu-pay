package com.minupay.settlement.domain;

import com.minupay.common.exception.ErrorCode;
import com.minupay.common.exception.MinuPayException;
import com.minupay.common.money.Money;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public class SettlementItem {

    private String id;
    private String paymentId;
    private String merchantId;
    private LocalDate targetDate;
    private Money grossAmount;
    private Money fee;
    private Money netAmount;
    private SettlementItemStatus status;
    private Instant cancelledAt;

    private SettlementItem() {}

    public static SettlementItem record(
            String paymentId,
            String merchantId,
            LocalDate targetDate,
            Money grossAmount,
            BigDecimal feeRate
    ) {
        Money fee = calculateFee(grossAmount, feeRate);
        SettlementItem item = new SettlementItem();
        item.id = UUID.randomUUID().toString();
        item.paymentId = paymentId;
        item.merchantId = merchantId;
        item.targetDate = targetDate;
        item.grossAmount = grossAmount;
        item.fee = fee;
        item.netAmount = grossAmount.subtract(fee);
        item.status = SettlementItemStatus.INCLUDED;
        return item;
    }

    public static SettlementItem of(
            String id, String paymentId, String merchantId, LocalDate targetDate,
            Money grossAmount, Money fee, Money netAmount,
            SettlementItemStatus status, Instant cancelledAt
    ) {
        SettlementItem item = new SettlementItem();
        item.id = id;
        item.paymentId = paymentId;
        item.merchantId = merchantId;
        item.targetDate = targetDate;
        item.grossAmount = grossAmount;
        item.fee = fee;
        item.netAmount = netAmount;
        item.status = status;
        item.cancelledAt = cancelledAt;
        return item;
    }

    public void cancel() {
        if (status == SettlementItemStatus.CANCELLED) {
            throw new MinuPayException(ErrorCode.INVALID_INPUT, "Already cancelled");
        }
        this.status = SettlementItemStatus.CANCELLED;
        this.cancelledAt = Instant.now();
    }

    private static Money calculateFee(Money gross, BigDecimal feeRate) {
        BigDecimal feeAmount = gross.getAmount().multiply(feeRate).setScale(0, RoundingMode.DOWN);
        return Money.of(feeAmount);
    }

    public String getId() {
        return id;
    }

    public String getPaymentId() {
        return paymentId;
    }

    public String getMerchantId() {
        return merchantId;
    }

    public LocalDate getTargetDate() {
        return targetDate;
    }

    public Money getGrossAmount() {
        return grossAmount;
    }

    public Money getFee() {
        return fee;
    }

    public Money getNetAmount() {
        return netAmount;
    }

    public SettlementItemStatus getStatus() {
        return status;
    }

    public Instant getCancelledAt() {
        return cancelledAt;
    }
}
