package com.minupay.audit.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.minupay.audit.domain.AuditLog;
import com.minupay.audit.domain.AuditLogRepository;
import com.minupay.common.event.EventEnvelope;
import com.minupay.common.event.EventTopic;
import com.minupay.common.event.EventType;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.support.Acknowledgment;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

class AuditConsumerTest {

    private AuditLogRepository auditLogRepository;
    private ObjectMapper objectMapper;
    private AuditConsumer consumer;

    @BeforeEach
    void setUp() {
        auditLogRepository = mock(AuditLogRepository.class);
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        consumer = new AuditConsumer(auditLogRepository, objectMapper);
    }

    @Test
    @DisplayName("정상_envelope_수신시_AuditLog가_저장되고_ack된다")
    void consume_success() throws Exception {
        EventEnvelope envelope = new EventEnvelope(
                "event-1", EventType.PAYMENT_APPROVED.wireName(), "payment-1", "Payment", "trace-1",
                Instant.parse("2026-04-19T10:00:00Z"),
                Map.of("paymentId", "payment-1", "amount", 1000)
        );
        String json = objectMapper.writeValueAsString(envelope);
        ConsumerRecord<String, String> record = new ConsumerRecord<>(EventTopic.PAYMENT_APPROVED, 0, 0L, "payment-1", json);
        Acknowledgment ack = mock(Acknowledgment.class);
        given(auditLogRepository.saveIfAbsent(any())).willReturn(true);

        consumer.consume(record, ack);

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).saveIfAbsent(captor.capture());
        AuditLog saved = captor.getValue();
        assertThat(saved.getEventId()).isEqualTo("event-1");
        assertThat(saved.getEventType()).isEqualTo(EventType.PAYMENT_APPROVED.wireName());
        assertThat(saved.getAggregateId()).isEqualTo("payment-1");
        assertThat(saved.getTraceId()).isEqualTo("trace-1");
        assertThat(saved.getPayload()).contains("paymentId");
        verify(ack).acknowledge();
    }

    @Test
    @DisplayName("중복_eventId_수신시_Repository가_false를_반환해도_ack된다")
    void consume_duplicate_stillAcks() throws Exception {
        EventEnvelope envelope = new EventEnvelope(
                "event-1", EventType.PAYMENT_APPROVED.wireName(), "payment-1", "Payment", null,
                Instant.now(), Map.of("paymentId", "payment-1")
        );
        String json = objectMapper.writeValueAsString(envelope);
        ConsumerRecord<String, String> record = new ConsumerRecord<>(EventTopic.PAYMENT_APPROVED, 0, 0L, "payment-1", json);
        Acknowledgment ack = mock(Acknowledgment.class);
        given(auditLogRepository.saveIfAbsent(any())).willReturn(false);

        consumer.consume(record, ack);

        verify(auditLogRepository).saveIfAbsent(any());
        verify(ack).acknowledge();
    }

    @Test
    @DisplayName("파싱_실패시_Repository_호출없이_ack하여_소비자가_멈추지_않는다")
    void consume_invalidJson_acksWithoutSaving() {
        ConsumerRecord<String, String> record = new ConsumerRecord<>(EventTopic.PAYMENT_APPROVED, 0, 0L, "key", "{broken");
        Acknowledgment ack = mock(Acknowledgment.class);

        consumer.consume(record, ack);

        verifyNoInteractions(auditLogRepository);
        verify(ack).acknowledge();
    }
}
