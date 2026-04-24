package com.minupay.wallet.presentation.dto;

import com.minupay.common.money.Money;
import com.minupay.wallet.application.dto.ChargeCommand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record WalletTxRequest(
        @NotNull @Positive BigDecimal amount,
        @NotBlank String reason,
        @NotBlank String idempotencyKey
) {
    public ChargeCommand toCommand(Long userId) {
        return new ChargeCommand(userId, Money.of(amount), idempotencyKey, reason, idempotencyKey);
    }
}
