package com.minupay.audit.domain;

import java.time.Instant;

public class AuditLog {

    private final String eventId;
    private final String eventType;
    private final String aggregateId;
    private final String aggregateType;
    private final String traceId;
    private final Instant occurredAt;
    private final String payload;
    private final Instant recordedAt;

    private AuditLog(String eventId, String eventType, String aggregateId, String aggregateType,
                     String traceId, Instant occurredAt, String payload, Instant recordedAt) {
        this.eventId = eventId;
        this.eventType = eventType;
        this.aggregateId = aggregateId;
        this.aggregateType = aggregateType;
        this.traceId = traceId;
        this.occurredAt = occurredAt;
        this.payload = payload;
        this.recordedAt = recordedAt;
    }

    public static AuditLog record(String eventId, String eventType, String aggregateId, String aggregateType,
                                  String traceId, Instant occurredAt, String payload) {
        return new AuditLog(eventId, eventType, aggregateId, aggregateType,
                traceId, occurredAt, payload, Instant.now());
    }

    public static AuditLog of(String eventId, String eventType, String aggregateId, String aggregateType,
                              String traceId, Instant occurredAt, String payload, Instant recordedAt) {
        return new AuditLog(eventId, eventType, aggregateId, aggregateType,
                traceId, occurredAt, payload, recordedAt);
    }

    public String getEventId() { return eventId; }
    public String getEventType() { return eventType; }
    public String getAggregateId() { return aggregateId; }
    public String getAggregateType() { return aggregateType; }
    public String getTraceId() { return traceId; }
    public Instant getOccurredAt() { return occurredAt; }
    public String getPayload() { return payload; }
    public Instant getRecordedAt() { return recordedAt; }
}
