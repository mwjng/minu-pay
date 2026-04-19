package com.minupay.common.outbox;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class OutboxPublisherTest {

    @Mock
    private OutboxRepository outboxRepository;

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    @InjectMocks
    private OutboxPublisher publisher;

    private Outbox pending;

    @BeforeEach
    void setUp() {
        pending = Outbox.create("agg-1", "Payment", "PaymentApproved",
                "payment.approved", "agg-1", "{\"eventId\":\"e-1\"}");
    }

    @Test
    @DisplayName("PENDING_Outbox_발행_성공시_PUBLISHED_상태로_변경된다")
    void publish_success_marksPublished() {
        given(outboxRepository.findTop50ByStatusOrderByCreatedAtAsc(OutboxStatus.PENDING))
                .willReturn(List.of(pending));
        SendResult<String, String> sendResult = new SendResult<>(
                new ProducerRecord<>("payment.approved", "agg-1", "{}"),
                new RecordMetadata(new TopicPartition("payment.approved", 0), 0, 0, 0, 0, 0)
        );
        given(kafkaTemplate.send(any(ProducerRecord.class)))
                .willReturn(CompletableFuture.completedFuture(sendResult));

        publisher.publish();

        assertThat(pending.getStatus()).isEqualTo(OutboxStatus.PUBLISHED);
        assertThat(pending.getPublishedAt()).isNotNull();
    }

    @Test
    @DisplayName("Kafka_전송_실패시_FAILED_상태로_변경되고_재시도_카운트가_증가한다")
    void publish_failure_marksFailed() {
        given(outboxRepository.findTop50ByStatusOrderByCreatedAtAsc(OutboxStatus.PENDING))
                .willReturn(List.of(pending));
        CompletableFuture<SendResult<String, String>> failed = new CompletableFuture<>();
        failed.completeExceptionally(new RuntimeException("broker down"));
        given(kafkaTemplate.send(any(ProducerRecord.class))).willReturn(failed);

        publisher.publish();

        assertThat(pending.getStatus()).isEqualTo(OutboxStatus.FAILED);
        assertThat(pending.getRetryCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("Kafka_헤더에_이벤트_메타데이터가_포함된다")
    void publish_attachesEventMetadataHeaders() {
        given(outboxRepository.findTop50ByStatusOrderByCreatedAtAsc(OutboxStatus.PENDING))
                .willReturn(List.of(pending));
        SendResult<String, String> sendResult = new SendResult<>(
                new ProducerRecord<>("payment.approved", "agg-1", "{}"),
                new RecordMetadata(new TopicPartition("payment.approved", 0), 0, 0, 0, 0, 0)
        );
        given(kafkaTemplate.send(any(ProducerRecord.class)))
                .willReturn(CompletableFuture.completedFuture(sendResult));

        publisher.publish();

        ArgumentCaptor<ProducerRecord<String, String>> captor = ArgumentCaptor.forClass(ProducerRecord.class);
        verify(kafkaTemplate).send(captor.capture());
        ProducerRecord<String, String> sent = captor.getValue();
        assertThat(sent.topic()).isEqualTo("payment.approved");
        assertThat(sent.key()).isEqualTo("agg-1");
        assertThat(sent.headers().lastHeader("eventType").value()).asString().isEqualTo("PaymentApproved");
        assertThat(sent.headers().lastHeader("aggregateType").value()).asString().isEqualTo("Payment");
        assertThat(sent.headers().lastHeader("aggregateId").value()).asString().isEqualTo("agg-1");
    }
}
