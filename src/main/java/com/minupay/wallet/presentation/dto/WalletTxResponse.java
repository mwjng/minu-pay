package com.minupay.wallet.presentation.dto;

import com.minupay.wallet.application.dto.WalletInfo;

import java.math.BigDecimal;

public record WalletTxResponse(
        Long walletId,
        BigDecimal amount,
        BigDecimal balanceAfter
) {
    public static WalletTxResponse of(WalletInfo info, BigDecimal amount) {
        return new WalletTxResponse(info.walletId(), amount, BigDecimal.valueOf(info.balance()));
    }
}
