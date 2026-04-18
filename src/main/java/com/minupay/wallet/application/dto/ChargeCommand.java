package com.minupay.wallet.application.dto;

import com.minupay.common.money.Money;

public record ChargeCommand(Long userId, Money amount, String referenceId, String referenceType) {}
