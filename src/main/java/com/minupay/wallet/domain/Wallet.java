package com.minupay.wallet.domain;

import com.minupay.common.event.DomainEvent;
import com.minupay.common.exception.ErrorCode;
import com.minupay.common.exception.MinuPayException;
import com.minupay.common.money.Money;
import com.minupay.wallet.domain.event.WalletCharged;
import com.minupay.wallet.domain.event.WalletDeducted;
import com.minupay.wallet.domain.event.WalletRefunded;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Wallet {

    private Long id;
    private Long userId;
    private Money balance;
    private WalletStatus status;
    private Long version;

    private final List<DomainEvent> domainEvents = new ArrayList<>();

    private Wallet() {}

    public static Wallet create(Long userId) {
        Wallet wallet = new Wallet();
        wallet.userId = userId;
        wallet.balance = Money.ZERO;
        wallet.status = WalletStatus.ACTIVE;
        return wallet;
    }

    public static Wallet of(Long id, Long userId, Money balance, WalletStatus status, Long version) {
        Wallet wallet = new Wallet();
        wallet.id = id;
        wallet.userId = userId;
        wallet.balance = balance;
        wallet.status = status;
        wallet.version = version;
        return wallet;
    }

    public WalletTransaction charge(Money amount, String referenceId, String referenceType) {
        validateActive();
        this.balance = this.balance.add(amount);
        WalletTransaction tx = WalletTransaction.create(id, WalletTransactionType.CHARGE, amount, referenceId, referenceType);
        domainEvents.add(new WalletCharged(id, userId, amount, referenceId));
        return tx;
    }

    public WalletTransaction deduct(Money amount, String referenceId, String referenceType) {
        validateActive();
        if (!balance.isGreaterThanOrEqualTo(amount)) {
            throw new MinuPayException(ErrorCode.INSUFFICIENT_BALANCE);
        }
        this.balance = this.balance.subtract(amount);
        WalletTransaction tx = WalletTransaction.create(id, WalletTransactionType.DEDUCT, amount, referenceId, referenceType);
        domainEvents.add(new WalletDeducted(id, userId, amount, referenceId));
        return tx;
    }

    public WalletTransaction refund(Money amount, String referenceId, String referenceType) {
        validateActive();
        this.balance = this.balance.add(amount);
        WalletTransaction tx = WalletTransaction.create(id, WalletTransactionType.REFUND, amount, referenceId, referenceType);
        domainEvents.add(new WalletRefunded(id, userId, amount, referenceId));
        return tx;
    }

    public void freeze() {
        validateActive();
        this.status = WalletStatus.FROZEN;
    }

    public void close() {
        this.status = WalletStatus.CLOSED;
    }

    private void validateActive() {
        if (status != WalletStatus.ACTIVE) {
            throw new MinuPayException(ErrorCode.WALLET_NOT_ACTIVE);
        }
    }

    public List<DomainEvent> getDomainEvents() {
        return Collections.unmodifiableList(domainEvents);
    }

    public void clearDomainEvents() {
        domainEvents.clear();
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public Money getBalance() { return balance; }
    public WalletStatus getStatus() { return status; }
    public Long getVersion() { return version; }
}
