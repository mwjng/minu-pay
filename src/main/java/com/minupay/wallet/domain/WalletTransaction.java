package com.minupay.wallet.domain;

import com.minupay.common.money.Money;

public class WalletTransaction {

    private Long id;
    private final Long walletId;
    private final WalletTransactionType type;
    private final Money amount;
    private final String referenceId;
    private final String referenceType;

    private WalletTransaction(Long walletId, WalletTransactionType type, Money amount, String referenceId, String referenceType) {
        this.walletId = walletId;
        this.type = type;
        this.amount = amount;
        this.referenceId = referenceId;
        this.referenceType = referenceType;
    }

    public static WalletTransaction create(Long walletId, WalletTransactionType type, Money amount, String referenceId, String referenceType) {
        return new WalletTransaction(walletId, type, amount, referenceId, referenceType);
    }

    public static WalletTransaction of(Long id, Long walletId, WalletTransactionType type, Money amount, String referenceId, String referenceType) {
        WalletTransaction tx = new WalletTransaction(walletId, type, amount, referenceId, referenceType);
        tx.id = id;
        return tx;
    }

    public Long getId() { return id; }
    public Long getWalletId() { return walletId; }
    public WalletTransactionType getType() { return type; }
    public Money getAmount() { return amount; }
    public String getReferenceId() { return referenceId; }
    public String getReferenceType() { return referenceType; }
}
