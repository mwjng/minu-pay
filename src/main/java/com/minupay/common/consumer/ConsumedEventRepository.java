package com.minupay.common.consumer;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;

public interface ConsumedEventRepository extends JpaRepository<ConsumedEventEntity, Long> {

    @Modifying
    @Query(
            value = "INSERT IGNORE INTO consumed_events (event_id, consumer_group, topic, consumed_at) "
                    + "VALUES (:eventId, :consumerGroup, :topic, :consumedAt)",
            nativeQuery = true
    )
    int insertIfAbsent(@Param("eventId") String eventId,
                       @Param("consumerGroup") String consumerGroup,
                       @Param("topic") String topic,
                       @Param("consumedAt") Instant consumedAt);
}
