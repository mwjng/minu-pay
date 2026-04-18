package com.minupay.payment.application.dto;

public record PaymentCommand(
        Long userId,
        String merchantId,
        long amount,
        String idempotencyKey,
        String tossPaymentKey
) {}
