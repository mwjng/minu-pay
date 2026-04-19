package com.minupay.common.outbox;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxPublisher {

    private static final long SEND_TIMEOUT_SECONDS = 5;

    private final OutboxRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    @Value("${outbox.publisher.max-retries:5}")
    private int maxRetries;

    @Scheduled(fixedDelayString = "${outbox.publisher.delay-ms:1000}")
    @Transactional
    public void publish() {
        List<Outbox> pendings = outboxRepository.findTop50ByStatusOrderByCreatedAtAsc(OutboxStatus.PENDING);
        if (pendings.isEmpty()) return;

        for (Outbox outbox : pendings) {
            try {
                ProducerRecord<String, String> record = new ProducerRecord<>(
                        outbox.getTopic(),
                        null,
                        outbox.getPartitionKey(),
                        outbox.getPayload()
                );
                record.headers()
                        .add(new RecordHeader("eventType", outbox.getEventType().getBytes(StandardCharsets.UTF_8)))
                        .add(new RecordHeader("aggregateType", outbox.getAggregateType().getBytes(StandardCharsets.UTF_8)))
                        .add(new RecordHeader("aggregateId", outbox.getAggregateId().getBytes(StandardCharsets.UTF_8)));

                kafkaTemplate.send(record).get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                outbox.markPublished();
            } catch (InterruptedException e) {
                // 스레드 종료 신호 — 이 루프를 중단하고 남은 항목은 다음 스케줄에서 재시도하도록 둔다.
                Thread.currentThread().interrupt();
                outbox.recordFailure(maxRetries);
                log.warn("Outbox publish interrupted id={}, stopping batch", outbox.getId());
                outboxRepository.saveAll(pendings);
                return;
            } catch (ExecutionException | TimeoutException e) {
                outbox.recordFailure(maxRetries);
                log.error("Outbox publish failed id={} topic={} retryCount={}",
                        outbox.getId(), outbox.getTopic(), outbox.getRetryCount(), e);
            }
        }
        outboxRepository.saveAll(pendings);
    }
}
