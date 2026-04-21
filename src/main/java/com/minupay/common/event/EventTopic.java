package com.minupay.common.event;

public final class EventTopic {

    public static final String WALLET_CHARGED = "wallet.charged";
    public static final String WALLET_DEDUCTED = "wallet.deducted";
    public static final String WALLET_REFUNDED = "wallet.refunded";
    public static final String PAYMENT_APPROVED = "payment.approved";
    public static final String PAYMENT_FAILED = "payment.failed";
    public static final String PAYMENT_CANCELLED = "payment.cancelled";

    private EventTopic() {
    }
}
