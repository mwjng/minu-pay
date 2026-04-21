package com.minupay.common.consumer;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(
        name = "consumed_events",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_event_group",
                columnNames = {"event_id", "consumer_group"}
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ConsumedEventEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String eventId;

    @Column(nullable = false)
    private String consumerGroup;

    @Column(nullable = false)
    private String topic;

    @Column(nullable = false)
    private Instant consumedAt;

    public static ConsumedEventEntity mark(String eventId, String consumerGroup, String topic) {
        ConsumedEventEntity entity = new ConsumedEventEntity();
        entity.eventId = eventId;
        entity.consumerGroup = consumerGroup;
        entity.topic = topic;
        entity.consumedAt = Instant.now();
        return entity;
    }
}
