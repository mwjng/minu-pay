package com.minupay.common.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Instant;

public record EventEnvelope(
        String eventId,
        String eventType,
        String aggregateId,
        String aggregateType,
        String traceId,
        Instant occurredAt,
        Object payload
) {
    public static EventEnvelope from(DomainEvent event) {
        return new EventEnvelope(
                event.getEventId(),
                event.getEventType(),
                event.getAggregateId(),
                event.getAggregateType(),
                event.getTraceId(),
                event.getOccurredAt(),
                event.getPayload()
        );
    }

    public String toJson(ObjectMapper objectMapper) throws JsonProcessingException {
        return objectMapper.writeValueAsString(this);
    }
}
