package com.minupay.wallet.application.dto;

import com.minupay.common.money.Money;

/**
 * idempotencyKey is required for external charge entrypoint; internal flows (deduct/refund) pass null
 * since their idempotency is already enforced upstream (payment's own key, outbox replay guard, etc.).
 */
public record ChargeCommand(
        Long userId,
        Money amount,
        String referenceId,
        String referenceType,
        String idempotencyKey
) {}
