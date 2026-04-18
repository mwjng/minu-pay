package com.minupay.wallet.application.dto;

import com.minupay.wallet.domain.Wallet;
import com.minupay.wallet.domain.WalletStatus;

public record WalletInfo(Long walletId, Long userId, long balance, WalletStatus status) {

    public static WalletInfo from(Wallet wallet) {
        return new WalletInfo(wallet.getId(), wallet.getUserId(), wallet.getBalance().toLong(), wallet.getStatus());
    }
}
