package com.minupay.payment.infrastructure;

import com.minupay.common.entity.BaseTimeEntity;
import com.minupay.common.money.Money;
import com.minupay.payment.domain.Payment;
import com.minupay.payment.domain.PaymentStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(
    name = "payments",
    indexes = {
        @Index(name = "idx_idempotency_key", columnList = "idempotency_key", unique = true),
        @Index(name = "idx_user_status", columnList = "user_id, status")
    }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PaymentEntity extends BaseTimeEntity {

    @Id
    private String id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private String merchantId;

    @Column(nullable = false)
    private Long amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus status;

    @Column(nullable = false, unique = true)
    private String idempotencyKey;

    private Long walletTransactionId;

    @Column(columnDefinition = "TEXT")
    private String failureReason;

    private Instant approvedAt;
    private Instant cancelledAt;

    public static PaymentEntity from(Payment payment) {
        PaymentEntity entity = new PaymentEntity();
        entity.id = payment.getId();
        entity.userId = payment.getUserId();
        entity.merchantId = payment.getMerchantId();
        entity.amount = payment.getAmount().toLong();
        entity.status = payment.getStatus();
        entity.idempotencyKey = payment.getIdempotencyKey();
        entity.walletTransactionId = payment.getWalletTransactionId();
        entity.failureReason = payment.getFailureReason();
        entity.approvedAt = payment.getApprovedAt();
        entity.cancelledAt = payment.getCancelledAt();
        return entity;
    }

    public Payment toDomain(com.minupay.payment.domain.PgPayment pgPayment) {
        return Payment.of(id, userId, merchantId, Money.of(amount), status,
                idempotencyKey, walletTransactionId, pgPayment, failureReason, approvedAt, cancelledAt);
    }
}
