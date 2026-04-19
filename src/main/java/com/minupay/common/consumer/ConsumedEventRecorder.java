package com.minupay.common.consumer;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ConsumedEventRecorder {

    private final ConsumedEventRepository repository;

    /**
     * Returns true if the event was newly recorded, false if it had already been consumed by the given group.
     * Must be called inside the caller's @Transactional so the marker and the business write commit together.
     */
    public boolean markIfAbsent(String eventId, String consumerGroup, String topic) {
        if (repository.existsByEventIdAndConsumerGroup(eventId, consumerGroup)) {
            return false;
        }
        try {
            repository.save(ConsumedEventEntity.mark(eventId, consumerGroup, topic));
            return true;
        } catch (DataIntegrityViolationException e) {
            // 동일 파티션은 그룹 내 1개 컨슈머만 소비하므로 이론상 도달 안 함. 방어적 처리.
            return false;
        }
    }
}
