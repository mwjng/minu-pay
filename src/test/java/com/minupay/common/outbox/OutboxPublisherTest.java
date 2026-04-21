package com.minupay.common.outbox;

import com.minupay.common.event.EventTopic;
import com.minupay.common.event.EventType;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.AfterEach;
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
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class OutboxPublisherTest {

    private static final int MAX_RETRIES = 5;

    @Mock
    private OutboxRepository outboxRepository;

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    @InjectMocks
    private OutboxPublisher publisher;

    private Outbox pending;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(publisher, "maxRetries", MAX_RETRIES);
        pending = Outbox.create("agg-1", "Payment", EventType.PAYMENT_APPROVED.wireName(),
                EventTopic.PAYMENT_APPROVED, "agg-1", "{\"eventId\":\"e-1\"}");
    }

    @AfterEach
    void clearInterrupt() {
        Thread.interrupted(); // 다른 테스트에 interrupt 플래그가 새지 않도록
    }

    @Test
    @DisplayName("PENDING_Outbox_발행_성공시_PUBLISHED_상태로_변경된다")
    void publish_success_marksPublished() {
        given(outboxRepository.findTop50ByStatusOrderByCreatedAtAsc(OutboxStatus.PENDING))
                .willReturn(List.of(pending));
        given(kafkaTemplate.send(any(ProducerRecord.class)))
                .willReturn(CompletableFuture.completedFuture(successResult()));

        publisher.publish();

        assertThat(pending.getStatus()).isEqualTo(OutboxStatus.PUBLISHED);
        assertThat(pending.getPublishedAt()).isNotNull();
    }

    @Test
    @DisplayName("Kafka_전송_실패시_재시도_카운트만_증가하고_PENDING_상태로_남는다")
    void publish_failure_keepsPending() {
        given(outboxRepository.findTop50ByStatusOrderByCreatedAtAsc(OutboxStatus.PENDING))
                .willReturn(List.of(pending));
        CompletableFuture<SendResult<String, String>> failed = new CompletableFuture<>();
        failed.completeExceptionally(new RuntimeException("broker down"));
        given(kafkaTemplate.send(any(ProducerRecord.class))).willReturn(failed);

        publisher.publish();

        assertThat(pending.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(pending.getRetryCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("재시도_횟수가_임계값에_도달하면_FAILED로_전환된다")
    void publish_retryExhausted_marksFailed() {
        ReflectionTestUtils.setField(publisher, "maxRetries", 2);
        given(outboxRepository.findTop50ByStatusOrderByCreatedAtAsc(OutboxStatus.PENDING))
                .willReturn(List.of(pending));
        CompletableFuture<SendResult<String, String>> failed = new CompletableFuture<>();
        failed.completeExceptionally(new RuntimeException("broker down"));
        given(kafkaTemplate.send(any(ProducerRecord.class))).willReturn(failed);

        publisher.publish(); // retry=1, PENDING
        publisher.publish(); // retry=2, FAILED

        assertThat(pending.getRetryCount()).isEqualTo(2);
        assertThat(pending.getStatus()).isEqualTo(OutboxStatus.FAILED);
    }

    @Test
    @DisplayName("인터럽트_발생시_현재_배치를_중단하고_남은_항목은_다음_스케줄로_넘긴다")
    void publish_interrupted_breaksLoop() {
        Outbox second = Outbox.create("agg-2", "Payment", EventType.PAYMENT_APPROVED.wireName(),
                EventTopic.PAYMENT_APPROVED, "agg-2", "{\"eventId\":\"e-2\"}");
        given(outboxRepository.findTop50ByStatusOrderByCreatedAtAsc(OutboxStatus.PENDING))
                .willReturn(List.of(pending, second));
        given(kafkaTemplate.send(any(ProducerRecord.class))).willReturn(new InterruptingFuture());

        publisher.publish();

        assertThat(pending.getRetryCount()).isZero();
        assertThat(pending.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(second.getRetryCount()).isZero();
        assertThat(second.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(Thread.currentThread().isInterrupted()).isTrue();
    }

    @Test
    @DisplayName("Kafka_헤더에_이벤트_메타데이터가_포함된다")
    void publish_attachesEventMetadataHeaders() {
        given(outboxRepository.findTop50ByStatusOrderByCreatedAtAsc(OutboxStatus.PENDING))
                .willReturn(List.of(pending));
        given(kafkaTemplate.send(any(ProducerRecord.class)))
                .willReturn(CompletableFuture.completedFuture(successResult()));

        publisher.publish();

        ArgumentCaptor<ProducerRecord<String, String>> captor = ArgumentCaptor.forClass(ProducerRecord.class);
        verify(kafkaTemplate).send(captor.capture());
        ProducerRecord<String, String> sent = captor.getValue();
        assertThat(sent.topic()).isEqualTo(EventTopic.PAYMENT_APPROVED);
        assertThat(sent.key()).isEqualTo("agg-1");
        assertThat(sent.headers().lastHeader("eventType").value()).asString().isEqualTo(EventType.PAYMENT_APPROVED.wireName());
        assertThat(sent.headers().lastHeader("aggregateType").value()).asString().isEqualTo("Payment");
        assertThat(sent.headers().lastHeader("aggregateId").value()).asString().isEqualTo("agg-1");
    }

    private SendResult<String, String> successResult() {
        return new SendResult<>(
                new ProducerRecord<>(EventTopic.PAYMENT_APPROVED, "agg-1", "{}"),
                new RecordMetadata(new TopicPartition(EventTopic.PAYMENT_APPROVED, 0), 0, 0, 0, 0, 0)
        );
    }

    private static class InterruptingFuture extends CompletableFuture<SendResult<String, String>> {
        @Override
        public SendResult<String, String> get(long timeout, TimeUnit unit) throws InterruptedException {
            throw new InterruptedException("simulated interrupt");
        }
    }
}
