package com.minupay.common.outbox;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
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
                Thread.currentThread().interrupt();
                outbox.markFailed();
                log.error("Outbox publish interrupted id={}", outbox.getId(), e);
            } catch (ExecutionException | TimeoutException e) {
                outbox.markFailed();
                log.error("Outbox publish failed id={} topic={}", outbox.getId(), outbox.getTopic(), e);
            }
        }
        outboxRepository.saveAll(pendings);
    }
}
