package com.minupay.common.event;

import java.util.Optional;

public enum EventType {

    WALLET_CHARGED("WalletCharged"),
    WALLET_DEDUCTED("WalletDeducted"),
    WALLET_REFUNDED("WalletRefunded"),

    PAYMENT_APPROVED("PaymentApproved"),
    PAYMENT_FAILED("PaymentFailed"),
    PAYMENT_CANCELLED("PaymentCancelled");

    private final String wireName;

    EventType(String wireName) {
        this.wireName = wireName;
    }

    public String wireName() {
        return wireName;
    }

    public static Optional<EventType> fromWire(String wireName) {
        for (EventType type : values()) {
            if (type.wireName.equals(wireName)) {
                return Optional.of(type);
            }
        }
        return Optional.empty();
    }
}
