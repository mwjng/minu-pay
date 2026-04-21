package com.minupay.wallet.domain.event;

import com.minupay.common.event.AbstractDomainEvent;
import com.minupay.common.event.EventTopic;
import com.minupay.common.money.Money;

import java.util.Map;

public class WalletCharged extends AbstractDomainEvent {

    private final Long walletId;
    private final Long userId;
    private final Money amount;
    private final String referenceId;

    public WalletCharged(Long walletId, Long userId, Money amount, String referenceId) {
        super();
        this.walletId = walletId;
        this.userId = userId;
        this.amount = amount;
        this.referenceId = referenceId;
    }

    @Override
    public String getEventType() {
        return "WalletCharged";
    }

    @Override
    public String getAggregateId() {
        return walletId.toString();
    }

    @Override
    public String getAggregateType() {
        return "Wallet";
    }

    @Override
    public String getTopic() {
        return EventTopic.WALLET_CHARGED;
    }

    @Override
    public Object getPayload() {
        return Map.of("walletId", walletId, "userId", userId, "amount", amount.toLong(), "referenceId", referenceId);
    }
}
