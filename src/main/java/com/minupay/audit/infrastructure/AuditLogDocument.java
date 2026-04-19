package com.minupay.audit.infrastructure;

import com.minupay.audit.domain.AuditLog;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Getter
@Document(collection = "audit_logs")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AuditLogDocument {

    @Id
    private String id;

    @Indexed(unique = true)
    private String eventId;

    @Indexed
    private String eventType;

    @Indexed
    private String aggregateId;

    private String aggregateType;
    private String traceId;
    private Instant occurredAt;
    private String payload;
    private Instant recordedAt;

    public static AuditLogDocument from(AuditLog log) {
        AuditLogDocument doc = new AuditLogDocument();
        doc.eventId = log.getEventId();
        doc.eventType = log.getEventType();
        doc.aggregateId = log.getAggregateId();
        doc.aggregateType = log.getAggregateType();
        doc.traceId = log.getTraceId();
        doc.occurredAt = log.getOccurredAt();
        doc.payload = log.getPayload();
        doc.recordedAt = log.getRecordedAt();
        return doc;
    }

    public AuditLog toDomain() {
        return AuditLog.of(eventId, eventType, aggregateId, aggregateType,
                traceId, occurredAt, payload, recordedAt);
    }
}
