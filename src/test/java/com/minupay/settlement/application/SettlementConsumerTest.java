package com.minupay.settlement.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class SettlementConsumerTest {

    private SettlementService settlementService;
    private ObjectMapper objectMapper;
    private SettlementConsumer consumer;

    @BeforeEach
    void setUp() {
        settlementService = mock(SettlementService.class);
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        consumer = new SettlementConsumer(settlementService, objectMapper);
    }

    @Test
    @DisplayName("PaymentApproved_envelope_수신시_handleApproved로_위임되고_ack된다")
    void consume_approved_routesToHandleApproved() throws Exception {
        EventEnvelope envelope = new EventEnvelope(
                "evt-1", EventType.PAYMENT_APPROVED, "payment-1", "Payment", "trace-1",
                Instant.parse("2026-04-19T10:00:00Z"),
                Map.of("paymentId", "payment-1", "merchantId", "m-1", "amount", 1000)
        );
        ConsumerRecord<String, String> record = new ConsumerRecord<>(
                EventTopic.PAYMENT_APPROVED, 0, 0L, "payment-1", objectMapper.writeValueAsString(envelope));
        Acknowledgment ack = mock(Acknowledgment.class);

        consumer.consume(record, ack);

        ArgumentCaptor<EventEnvelope> envCaptor = ArgumentCaptor.forClass(EventEnvelope.class);
        ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
        verify(settlementService).handleApproved(envCaptor.capture(), eq(EventTopic.PAYMENT_APPROVED), payloadCaptor.capture());
        assertThat(envCaptor.getValue().eventId()).isEqualTo("evt-1");
        assertThat(payloadCaptor.getValue()).containsEntry("paymentId", "payment-1");
        verifyNoMoreInteractions(settlementService);
        verify(ack).acknowledge();
    }

    @Test
    @DisplayName("PaymentCancelled_envelope_수신시_handleCancelled로_위임되고_ack된다")
    void consume_cancelled_routesToHandleCancelled() throws Exception {
        EventEnvelope envelope = new EventEnvelope(
                "evt-2", EventType.PAYMENT_CANCELLED, "payment-1", "Payment", null,
                Instant.now(), Map.of("paymentId", "payment-1")
        );
        ConsumerRecord<String, String> record = new ConsumerRecord<>(
                EventTopic.PAYMENT_CANCELLED, 0, 0L, "payment-1", objectMapper.writeValueAsString(envelope));
        Acknowledgment ack = mock(Acknowledgment.class);

        consumer.consume(record, ack);

        verify(settlementService).handleCancelled(any(), eq(EventTopic.PAYMENT_CANCELLED), any());
        verifyNoMoreInteractions(settlementService);
        verify(ack).acknowledge();
    }

    @Test
    @DisplayName("알_수_없는_eventType은_Service_호출없이_ack한다")
    void consume_unknownEventType_skipsServiceButAcks() throws Exception {
        EventEnvelope envelope = new EventEnvelope(
                "evt-x", "PaymentRefunded", "payment-1", "Payment", null,
                Instant.now(), Map.of("paymentId", "payment-1")
        );
        ConsumerRecord<String, String> record = new ConsumerRecord<>(
                EventTopic.PAYMENT_APPROVED, 0, 0L, "payment-1", objectMapper.writeValueAsString(envelope));
        Acknowledgment ack = mock(Acknowledgment.class);

        consumer.consume(record, ack);

        verifyNoInteractions(settlementService);
        verify(ack).acknowledge();
    }

    @Test
    @DisplayName("파싱_실패시_Service_호출없이_ack한다_poison_message")
    void consume_invalidJson_acksWithoutSaving() {
        ConsumerRecord<String, String> record = new ConsumerRecord<>(
                EventTopic.PAYMENT_APPROVED, 0, 0L, "k", "{broken");
        Acknowledgment ack = mock(Acknowledgment.class);

        consumer.consume(record, ack);

        verifyNoInteractions(settlementService);
        verify(ack).acknowledge();
    }
}
