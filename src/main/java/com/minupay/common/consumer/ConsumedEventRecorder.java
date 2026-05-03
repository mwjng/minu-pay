package com.minupay.common.consumer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConsumedEventRecorder {

    private final ConsumedEventRepository repository;

    public boolean markIfAbsent(String eventId, String consumerGroup, String topic) {
        int inserted = repository.insertIfAbsent(eventId, consumerGroup, topic, Instant.now());
        if (inserted == 0) {
            log.debug("Skip already-consumed event {}", eventId);
            return false;
        }
        return true;
    }
}
