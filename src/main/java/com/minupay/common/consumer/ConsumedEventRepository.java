package com.minupay.common.consumer;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ConsumedEventRepository extends JpaRepository<ConsumedEventEntity, Long> {
    boolean existsByEventIdAndConsumerGroup(String eventId, String consumerGroup);
}
