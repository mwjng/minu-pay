package com.minupay.settlement.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.minupay.common.event.EventEnvelope;
import com.minupay.common.event.EventTopic;
import com.minupay.common.event.EventType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class SettlementConsumer {

    private final SettlementService settlementService;
    private final ObjectMapper objectMapper;

    @KafkaListener(
            topics = {EventTopic.PAYMENT_APPROVED, EventTopic.PAYMENT_CANCELLED},
            groupId = "${settlement.consumer.group-id:settlement-consumer-group}"
    )
    public void consume(ConsumerRecord<String, String> record, Acknowledgment ack) {
        try {
            EventEnvelope envelope = objectMapper.readValue(record.value(), EventEnvelope.class);
            Map<String, Object> payload = toMap(envelope.payload());
            dispatch(envelope, record.topic(), payload);
            ack.acknowledge();
        } catch (JsonProcessingException e) {
            log.error("Failed to deserialize envelope topic={} offset={}", record.topic(), record.offset(), e);
            // poison message — ack해서 소비자 루프 멈추지 않도록. 운영에서는 DLQ로 보내야 함.
            ack.acknowledge();
        }
    }

    private void dispatch(EventEnvelope envelope, String topic, Map<String, Object> payload) {
        EventType.fromWire(envelope.eventType()).ifPresentOrElse(
                type -> route(type, envelope, topic, payload),
                () -> log.warn("Unhandled event type {} on {}", envelope.eventType(), topic)
        );
    }

    private void route(EventType type, EventEnvelope envelope, String topic, Map<String, Object> payload) {
        switch (type) {
            case PAYMENT_APPROVED -> settlementService.handleApproved(envelope, topic, payload);
            case PAYMENT_CANCELLED -> settlementService.handleCancelled(envelope, topic, payload);
            default -> log.warn("Known but unrouted event type {} on {}", type.wireName(), topic);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> toMap(Object payload) {
        if (payload instanceof Map<?, ?> m) return (Map<String, Object>) m;
        return Map.of();
    }
}
