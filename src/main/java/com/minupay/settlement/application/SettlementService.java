package com.minupay.settlement.application;

import com.minupay.common.consumer.ConsumedEventRecorder;
import com.minupay.common.event.EventEnvelope;
import com.minupay.common.money.Money;
import com.minupay.settlement.domain.SettlementItem;
import com.minupay.settlement.domain.SettlementItemRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class SettlementService {

    private static final ZoneId SETTLEMENT_ZONE = ZoneId.of("Asia/Seoul");

    private final SettlementItemRepository settlementItemRepository;
    private final ConsumedEventRecorder consumedEventRecorder;

    @Value("${settlement.fee-rate:0.03}")
    private BigDecimal feeRate;

    @Value("${settlement.consumer.group-id:settlement-consumer-group}")
    private String consumerGroup;

    @Transactional
    public void handleApproved(EventEnvelope envelope, String topic, Map<String, Object> payload) {
        if (!consumedEventRecorder.markIfAbsent(envelope.eventId(), consumerGroup, topic)) {
            log.debug("Skip already-consumed event {}", envelope.eventId());
            return;
        }
        String paymentId = (String) payload.get("paymentId");
        String merchantId = (String) payload.get("merchantId");
        long amount = ((Number) payload.get("amount")).longValue();
        LocalDate targetDate = envelope.occurredAt().atZone(SETTLEMENT_ZONE).toLocalDate();

        SettlementItem item = SettlementItem.record(
                paymentId, merchantId, targetDate, Money.of(amount), feeRate
        );
        settlementItemRepository.save(item);
    }

    @Transactional
    public void handleCancelled(EventEnvelope envelope, String topic, Map<String, Object> payload) {
        if (!consumedEventRecorder.markIfAbsent(envelope.eventId(), consumerGroup, topic)) {
            log.debug("Skip already-consumed event {}", envelope.eventId());
            return;
        }
        String paymentId = (String) payload.get("paymentId");

        settlementItemRepository.findByPaymentId(paymentId).ifPresentOrElse(
                item -> {
                    item.cancel();
                    settlementItemRepository.save(item);
                },
                () -> log.warn("PaymentCancelled for unknown settlement item paymentId={}", paymentId)
        );
    }
}
