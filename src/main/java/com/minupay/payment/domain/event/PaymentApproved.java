package com.minupay.payment.domain.event;

import com.minupay.common.event.AbstractDomainEvent;
import com.minupay.common.event.EventTopic;
import com.minupay.common.event.EventType;
import com.minupay.common.money.Money;

import java.util.Map;

public class PaymentApproved extends AbstractDomainEvent {

    private final String paymentId;
    private final Long userId;
    private final String merchantId;
    private final Money amount;

    public PaymentApproved(String paymentId, Long userId, String merchantId, Money amount) {
        super();
        this.paymentId = paymentId;
        this.userId = userId;
        this.merchantId = merchantId;
        this.amount = amount;
    }

    @Override
    public String getEventType() {
        return EventType.PAYMENT_APPROVED;
    }

    @Override
    public String getAggregateId() {
        return paymentId;
    }

    @Override
    public String getAggregateType() {
        return "Payment";
    }

    @Override
    public String getTopic() {
        return EventTopic.PAYMENT_APPROVED;
    }

    @Override
    public Object getPayload() {
        return Map.of("paymentId", paymentId, "userId", userId, "merchantId", merchantId, "amount", amount.toLong());
    }
}
