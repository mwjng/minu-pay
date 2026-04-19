package com.minupay.settlement.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.minupay.common.config.KafkaConfig;
import com.minupay.common.event.EventEnvelope;
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
            topics = {KafkaConfig.TOPIC_PAYMENT_APPROVED, KafkaConfig.TOPIC_PAYMENT_CANCELLED},
            groupId = "${settlement.consumer.group-id:settlement-consumer-group}"
    )
    public void consume(ConsumerRecord<String, String> record, Acknowledgment ack) {
        try {
            EventEnvelope envelope = objectMapper.readValue(record.value(), EventEnvelope.class);
            Map<String, Object> payload = toMap(envelope.payload());

            switch (envelope.eventType()) {
                case "PaymentApproved" -> settlementService.handleApproved(envelope, record.topic(), payload);
                case "PaymentCancelled" -> settlementService.handleCancelled(envelope, record.topic(), payload);
                default -> log.warn("Unhandled event type {} on {}", envelope.eventType(), record.topic());
            }
            ack.acknowledge();
        } catch (JsonProcessingException e) {
            log.error("Failed to deserialize envelope topic={} offset={}", record.topic(), record.offset(), e);
            // poison message — ack해서 소비자 루프 멈추지 않도록. 운영에서는 DLQ로 보내야 함.
            ack.acknowledge();
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> toMap(Object payload) {
        if (payload instanceof Map<?, ?> m) return (Map<String, Object>) m;
        return Map.of();
    }
}
