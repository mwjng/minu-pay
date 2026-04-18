package com.minupay.common.outbox;

import com.minupay.common.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(
    name = "outbox",
    indexes = @Index(name = "idx_status_created", columnList = "status, created_at")
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Outbox extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String aggregateId;

    @Column(nullable = false)
    private String aggregateType;

    @Column(nullable = false)
    private String eventType;

    @Column(nullable = false)
    private String topic;

    private String partitionKey;

    @Column(columnDefinition = "JSON", nullable = false)
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OutboxStatus status;

    private int retryCount;

    private Instant publishedAt;

    public static Outbox create(
            String aggregateId,
            String aggregateType,
            String eventType,
            String topic,
            String partitionKey,
            String payload
    ) {
        Outbox outbox = new Outbox();
        outbox.aggregateId = aggregateId;
        outbox.aggregateType = aggregateType;
        outbox.eventType = eventType;
        outbox.topic = topic;
        outbox.partitionKey = partitionKey;
        outbox.payload = payload;
        outbox.status = OutboxStatus.PENDING;
        outbox.retryCount = 0;
        return outbox;
    }

    public void markPublished() {
        this.status = OutboxStatus.PUBLISHED;
        this.publishedAt = Instant.now();
    }

    public void markFailed() {
        this.retryCount++;
        this.status = OutboxStatus.FAILED;
    }

    public void resetToPending() {
        this.status = OutboxStatus.PENDING;
    }
}
