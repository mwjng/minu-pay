package com.minupay.common.event;

import org.slf4j.MDC;

import java.time.Instant;
import java.util.UUID;

public abstract class AbstractDomainEvent implements DomainEvent {

    private static final String MDC_TRACE_KEY = "traceId";

    private final String eventId;
    private final String traceId;
    private final Instant occurredAt;

    protected AbstractDomainEvent() {
        this.eventId = UUID.randomUUID().toString();
        this.traceId = MDC.get(MDC_TRACE_KEY);
        this.occurredAt = Instant.now();
    }

    @Override public String getEventId() { return eventId; }
    @Override public String getTraceId() { return traceId; }
    @Override public Instant getOccurredAt() { return occurredAt; }

    @Override
    public SnapshotPair getSnapshot() { return null; }
}
