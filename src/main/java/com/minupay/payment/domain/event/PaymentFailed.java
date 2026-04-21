package com.minupay.payment.domain.event;

import com.minupay.common.event.AbstractDomainEvent;
import com.minupay.common.event.EventTopic;
import com.minupay.common.money.Money;

import java.util.Map;

public class PaymentFailed extends AbstractDomainEvent {

    private final String paymentId;
    private final Long userId;
    private final Money amount;
    private final String reason;

    public PaymentFailed(String paymentId, Long userId, Money amount, String reason) {
        super();
        this.paymentId = paymentId;
        this.userId = userId;
        this.amount = amount;
        this.reason = reason;
    }

    @Override
    public String getEventType() {
        return "PaymentFailed";
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
        return EventTopic.PAYMENT_FAILED;
    }

    @Override
    public Object getPayload() {
        return Map.of("paymentId", paymentId, "userId", userId, "amount", amount.toLong(), "reason", reason);
    }
}
