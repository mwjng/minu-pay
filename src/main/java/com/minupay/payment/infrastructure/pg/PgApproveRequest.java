package com.minupay.payment.infrastructure.pg;

public record PgApproveRequest(String paymentKey, String orderId, long amount) {}
