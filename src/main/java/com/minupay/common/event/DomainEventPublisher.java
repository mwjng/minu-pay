package com.minupay.common.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.minupay.common.exception.ErrorCode;
import com.minupay.common.exception.MinuPayException;
import com.minupay.common.outbox.Outbox;
import com.minupay.common.outbox.OutboxRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class DomainEventPublisher {

    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public void publish(List<? extends DomainEvent> events) {
        events.forEach(this::publishOne);
    }

    private void publishOne(DomainEvent event) {
        String payload = serialize(event);
        outboxRepository.save(Outbox.create(
                event.getAggregateId(),
                event.getAggregateType(),
                event.getEventType(),
                event.getTopic(),
                event.getAggregateId(),
                payload
        ));
    }

    private String serialize(DomainEvent event) {
        try {
            return EventEnvelope.from(event).toJson(objectMapper);
        } catch (JsonProcessingException e) {
            throw new MinuPayException(ErrorCode.INTERNAL_ERROR, "Event serialization failed");
        }
    }
}
