package com.minupay.settlement.application;

import com.minupay.common.consumer.ConsumedEventRecorder;
import com.minupay.common.event.EventEnvelope;
import com.minupay.common.event.EventTopic;
import com.minupay.common.event.EventType;
import com.minupay.common.money.Money;
import com.minupay.settlement.domain.SettlementItem;
import com.minupay.settlement.domain.SettlementItemRepository;
import com.minupay.settlement.domain.SettlementItemStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SettlementServiceTest {

    private static final String GROUP = "settlement-consumer-group";
    private static final String TOPIC_APPROVED = EventTopic.PAYMENT_APPROVED;
    private static final String TOPIC_CANCELLED = EventTopic.PAYMENT_CANCELLED;

    @Mock
    private SettlementItemRepository settlementItemRepository;

    @Mock
    private ConsumedEventRecorder consumedEventRecorder;

    @InjectMocks
    private SettlementService settlementService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(settlementService, "feeRate", new BigDecimal("0.03"));
        ReflectionTestUtils.setField(settlementService, "consumerGroup", GROUP);
    }

    @Test
    @DisplayName("PaymentApproved_수신시_SettlementItem이_저장된다")
    void handleApproved_savesItem() {
        EventEnvelope envelope = envelope("evt-1", EventType.PAYMENT_APPROVED.wireName(), "payment-1",
                Instant.parse("2026-04-19T01:00:00Z"));
        Map<String, Object> payload = Map.of(
                "paymentId", "payment-1",
                "merchantId", "merchant-1",
                "amount", 10_000
        );
        given(consumedEventRecorder.markIfAbsent("evt-1", GROUP, TOPIC_APPROVED)).willReturn(true);

        settlementService.handleApproved(envelope, TOPIC_APPROVED, payload);

        ArgumentCaptor<SettlementItem> captor = ArgumentCaptor.forClass(SettlementItem.class);
        verify(settlementItemRepository).save(captor.capture());
        SettlementItem saved = captor.getValue();
        assertThat(saved.getPaymentId()).isEqualTo("payment-1");
        assertThat(saved.getMerchantId()).isEqualTo("merchant-1");
        assertThat(saved.getGrossAmount()).isEqualTo(Money.of(10_000));
        assertThat(saved.getFee()).isEqualTo(Money.of(300));
        assertThat(saved.getNetAmount()).isEqualTo(Money.of(9_700));
        assertThat(saved.getStatus()).isEqualTo(SettlementItemStatus.INCLUDED);
        // Asia/Seoul 기준: 2026-04-19T01:00:00Z == 2026-04-19T10:00 KST → 날짜는 2026-04-19
        assertThat(saved.getTargetDate()).isEqualTo(LocalDate.of(2026, 4, 19));
    }

    @Test
    @DisplayName("UTC_자정_직전의_approvedAt은_KST_기준_다음날짜로_정산된다")
    void handleApproved_targetDateUsesKst() {
        EventEnvelope envelope = envelope("evt-2", EventType.PAYMENT_APPROVED.wireName(), "payment-2",
                Instant.parse("2026-04-18T15:30:00Z")); // KST 00:30 2026-04-19
        Map<String, Object> payload = Map.of(
                "paymentId", "payment-2",
                "merchantId", "merchant-1",
                "amount", 5_000
        );
        given(consumedEventRecorder.markIfAbsent("evt-2", GROUP, TOPIC_APPROVED)).willReturn(true);

        settlementService.handleApproved(envelope, TOPIC_APPROVED, payload);

        ArgumentCaptor<SettlementItem> captor = ArgumentCaptor.forClass(SettlementItem.class);
        verify(settlementItemRepository).save(captor.capture());
        assertThat(captor.getValue().getTargetDate()).isEqualTo(LocalDate.of(2026, 4, 19));
    }

    @Test
    @DisplayName("중복_eventId면_ConsumedEventRecorder가_false반환하여_저장되지_않는다")
    void handleApproved_duplicateSkipped() {
        EventEnvelope envelope = envelope("evt-dup", EventType.PAYMENT_APPROVED.wireName(), "payment-1", Instant.now());
        given(consumedEventRecorder.markIfAbsent("evt-dup", GROUP, TOPIC_APPROVED)).willReturn(false);

        settlementService.handleApproved(envelope, TOPIC_APPROVED, Map.of(
                "paymentId", "payment-1", "merchantId", "m", "amount", 1000
        ));

        verify(settlementItemRepository, never()).save(any());
    }

    @Test
    @DisplayName("PaymentCancelled_수신시_대응하는_SettlementItem이_CANCELLED로_바뀐다")
    void handleCancelled_marksItemCancelled() {
        SettlementItem existing = SettlementItem.record(
                "payment-1", "merchant-1", LocalDate.of(2026, 4, 19),
                Money.of(10_000), new BigDecimal("0.03")
        );
        EventEnvelope envelope = envelope("evt-3", EventType.PAYMENT_CANCELLED.wireName(), "payment-1", Instant.now());
        given(consumedEventRecorder.markIfAbsent("evt-3", GROUP, TOPIC_CANCELLED)).willReturn(true);
        given(settlementItemRepository.findByPaymentId("payment-1")).willReturn(Optional.of(existing));

        settlementService.handleCancelled(envelope, TOPIC_CANCELLED, Map.of("paymentId", "payment-1"));

        assertThat(existing.getStatus()).isEqualTo(SettlementItemStatus.CANCELLED);
        assertThat(existing.getCancelledAt()).isNotNull();
        verify(settlementItemRepository).save(existing);
    }

    @Test
    @DisplayName("PaymentCancelled_수신했는데_해당_SettlementItem이_없으면_저장없이_종료한다")
    void handleCancelled_missingItem_noOp() {
        EventEnvelope envelope = envelope("evt-4", EventType.PAYMENT_CANCELLED.wireName(), "payment-missing", Instant.now());
        given(consumedEventRecorder.markIfAbsent("evt-4", GROUP, TOPIC_CANCELLED)).willReturn(true);
        given(settlementItemRepository.findByPaymentId("payment-missing")).willReturn(Optional.empty());

        settlementService.handleCancelled(envelope, TOPIC_CANCELLED, Map.of("paymentId", "payment-missing"));

        verify(settlementItemRepository, never()).save(any());
    }

    @Test
    @DisplayName("중복_PaymentCancelled_이벤트는_두번째_수신시_조회없이_스킵된다")
    void handleCancelled_duplicateSkipped() {
        EventEnvelope envelope = envelope("evt-dup-cancel", EventType.PAYMENT_CANCELLED.wireName(), "payment-1", Instant.now());
        given(consumedEventRecorder.markIfAbsent("evt-dup-cancel", GROUP, TOPIC_CANCELLED)).willReturn(false);

        settlementService.handleCancelled(envelope, TOPIC_CANCELLED, Map.of("paymentId", "payment-1"));

        verify(settlementItemRepository, never()).findByPaymentId(any());
        verify(settlementItemRepository, never()).save(any());
    }

    private EventEnvelope envelope(String eventId, String eventType, String aggregateId, Instant occurredAt) {
        return new EventEnvelope(eventId, eventType, aggregateId, "Payment", null, occurredAt, null);
    }
}
