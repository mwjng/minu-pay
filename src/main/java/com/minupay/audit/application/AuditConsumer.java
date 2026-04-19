package com.minupay.audit.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.minupay.audit.domain.AuditLog;
import com.minupay.audit.domain.AuditLogRepository;
import com.minupay.common.event.EventEnvelope;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AuditConsumer {

    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;

    @KafkaListener(
            topicPattern = "(wallet|payment)\\..*",
            groupId = "${audit.consumer.group-id:audit-consumer-group}"
    )
    public void consume(ConsumerRecord<String, String> record, Acknowledgment ack) {
        try {
            EventEnvelope envelope = objectMapper.readValue(record.value(), EventEnvelope.class);
            if (envelope.traceId() != null) {
                MDC.put("traceId", envelope.traceId());
            }
            try {
                Object payload = envelope.payload();
                String payloadJson = payload == null ? null : objectMapper.writeValueAsString(payload);

                AuditLog auditLog = AuditLog.record(
                        envelope.eventId(),
                        envelope.eventType(),
                        envelope.aggregateId(),
                        envelope.aggregateType(),
                        envelope.traceId(),
                        envelope.occurredAt(),
                        payloadJson
                );
                auditLogRepository.saveIfAbsent(auditLog);
                ack.acknowledge();
            } finally {
                MDC.remove("traceId");
            }
        } catch (JsonProcessingException e) {
            log.error("Failed to deserialize envelope topic={} offset={}", record.topic(), record.offset(), e);
            // poison message — ack해서 소비자 루프 멈추지 않도록. 운영에서는 DLQ로 보내야 함.
            ack.acknowledge();
        }
    }
}
