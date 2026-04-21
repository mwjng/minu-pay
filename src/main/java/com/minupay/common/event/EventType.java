package com.minupay.common.event;

public final class EventType {

    public static final String WALLET_CHARGED = "WalletCharged";
    public static final String WALLET_DEDUCTED = "WalletDeducted";
    public static final String WALLET_REFUNDED = "WalletRefunded";

    public static final String PAYMENT_APPROVED = "PaymentApproved";
    public static final String PAYMENT_FAILED = "PaymentFailed";
    public static final String PAYMENT_CANCELLED = "PaymentCancelled";

    private EventType() {
    }
}
