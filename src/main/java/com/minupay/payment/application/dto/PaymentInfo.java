package com.minupay.payment.application.dto;

import com.minupay.payment.domain.Payment;
import com.minupay.payment.domain.PaymentStatus;

import java.time.Instant;

public record PaymentInfo(
        String paymentId,
        Long userId,
        String merchantId,
        long amount,
        PaymentStatus status,
        String idempotencyKey,
        Instant approvedAt,
        Instant cancelledAt,
        String failureReason
) {
    public static PaymentInfo from(Payment payment) {
        return new PaymentInfo(
                payment.getId(),
                payment.getUserId(),
                payment.getMerchantId(),
                payment.getAmount().toLong(),
                payment.getStatus(),
                payment.getIdempotencyKey(),
                payment.getApprovedAt(),
                payment.getCancelledAt(),
                payment.getFailureReason()
        );
    }
}
