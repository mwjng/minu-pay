package com.minupay.common.event;

import java.time.Instant;

public interface DomainEvent {
    String getEventId();
    String getEventType();
    String getAggregateId();
    String getAggregateType();
    String getTraceId();
    Instant getOccurredAt();
    Object getPayload();
    SnapshotPair getSnapshot();

    record SnapshotPair(Object before, Object after) {}
}
