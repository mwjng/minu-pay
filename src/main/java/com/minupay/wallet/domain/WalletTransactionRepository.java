package com.minupay.wallet.domain;

public interface WalletTransactionRepository {
    WalletTransaction save(WalletTransaction transaction);
}
