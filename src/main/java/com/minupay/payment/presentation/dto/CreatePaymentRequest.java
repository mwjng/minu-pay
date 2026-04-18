package com.minupay.payment.presentation.dto;

import com.minupay.payment.application.dto.PaymentCommand;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreatePaymentRequest(
        @NotBlank String merchantId,
        @NotNull @Min(1) Long amount,
        @NotBlank String idempotencyKey,
        @NotBlank String tossPaymentKey
) {
    public PaymentCommand toCommand(Long userId) {
        return new PaymentCommand(userId, merchantId, amount, idempotencyKey, tossPaymentKey);
    }
}
