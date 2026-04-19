package com.minupay.wallet.presentation.dto;

import com.minupay.common.money.Money;
import com.minupay.wallet.application.dto.ChargeCommand;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ChargeRequest(
        @NotNull @Min(1) Long amount,
        @NotBlank String referenceId,
        @NotBlank String referenceType,
        @NotBlank String idempotencyKey
) {
    public ChargeCommand toCommand(Long userId) {
        return new ChargeCommand(userId, Money.of(amount), referenceId, referenceType, idempotencyKey);
    }
}
