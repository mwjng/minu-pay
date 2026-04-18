package com.minupay.payment.domain;

import com.minupay.common.event.DomainEvent;
import com.minupay.common.exception.ErrorCode;
import com.minupay.common.exception.MinuPayException;
import com.minupay.common.money.Money;
import com.minupay.payment.domain.event.PaymentApproved;
import com.minupay.payment.domain.event.PaymentCancelled;
import com.minupay.payment.domain.event.PaymentFailed;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class Payment {

    private String id;
    private Long userId;
    private String merchantId;
    private Money amount;
    private PaymentStatus status;
    private String idempotencyKey;
    private Long walletTransactionId;
    private PgPayment pgPayment;
    private String failureReason;
    private Instant approvedAt;
    private Instant cancelledAt;

    private final List<DomainEvent> domainEvents = new ArrayList<>();

    private Payment() {}

    public static Payment create(Long userId, String merchantId, Money amount, String idempotencyKey) {
        Payment payment = new Payment();
        payment.id = UUID.randomUUID().toString();
        payment.userId = userId;
        payment.merchantId = merchantId;
        payment.amount = amount;
        payment.status = PaymentStatus.PENDING;
        payment.idempotencyKey = idempotencyKey;
        return payment;
    }

    public static Payment of(String id, Long userId, String merchantId, Money amount,
                             PaymentStatus status, String idempotencyKey,
                             Long walletTransactionId, PgPayment pgPayment,
                             String failureReason, Instant approvedAt, Instant cancelledAt) {
        Payment payment = new Payment();
        payment.id = id;
        payment.userId = userId;
        payment.merchantId = merchantId;
        payment.amount = amount;
        payment.status = status;
        payment.idempotencyKey = idempotencyKey;
        payment.walletTransactionId = walletTransactionId;
        payment.pgPayment = pgPayment;
        payment.failureReason = failureReason;
        payment.approvedAt = approvedAt;
        payment.cancelledAt = cancelledAt;
        return payment;
    }

    public void approve(Long walletTransactionId, PgPayment pgPayment) {
        if (status != PaymentStatus.PENDING) {
            throw new MinuPayException(ErrorCode.INVALID_PAYMENT_STATUS, "Only PENDING payment can be approved");
        }
        this.walletTransactionId = walletTransactionId;
        this.pgPayment = pgPayment;
        this.status = PaymentStatus.APPROVED;
        this.approvedAt = Instant.now();
        domainEvents.add(new PaymentApproved(id, userId, merchantId, amount));
    }

    public void fail(String reason) {
        if (status != PaymentStatus.PENDING) {
            throw new MinuPayException(ErrorCode.INVALID_PAYMENT_STATUS, "Only PENDING payment can be failed");
        }
        this.failureReason = reason;
        this.status = PaymentStatus.FAILED;
        domainEvents.add(new PaymentFailed(id, userId, amount, reason));
    }

    public void cancel(PgPayment cancelPgPayment) {
        if (status != PaymentStatus.APPROVED) {
            throw new MinuPayException(ErrorCode.INVALID_PAYMENT_STATUS, "Only APPROVED payment can be cancelled");
        }
        this.pgPayment = cancelPgPayment;
        this.status = PaymentStatus.CANCELLED;
        this.cancelledAt = Instant.now();
        domainEvents.add(new PaymentCancelled(id, userId, merchantId, amount));
    }

    public List<DomainEvent> getDomainEvents() { return Collections.unmodifiableList(domainEvents); }
    public void clearDomainEvents() { domainEvents.clear(); }

    public String getId() { return id; }
    public Long getUserId() { return userId; }
    public String getMerchantId() { return merchantId; }
    public Money getAmount() { return amount; }
    public PaymentStatus getStatus() { return status; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public Long getWalletTransactionId() { return walletTransactionId; }
    public PgPayment getPgPayment() { return pgPayment; }
    public String getFailureReason() { return failureReason; }
    public Instant getApprovedAt() { return approvedAt; }
    public Instant getCancelledAt() { return cancelledAt; }
}
