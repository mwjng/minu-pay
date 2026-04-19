package com.minupay.audit.application.dto;

import com.minupay.audit.domain.AuditLog;

import java.time.Instant;

public record AuditLogView(
        String eventId,
        String eventType,
        String aggregateId,
        String aggregateType,
        String traceId,
        Instant occurredAt,
        Instant recordedAt,
        String payload
) {
    public static AuditLogView from(AuditLog log) {
        return new AuditLogView(
                log.getEventId(),
                log.getEventType(),
                log.getAggregateId(),
                log.getAggregateType(),
                log.getTraceId(),
                log.getOccurredAt(),
                log.getRecordedAt(),
                log.getPayload()
        );
    }
}
