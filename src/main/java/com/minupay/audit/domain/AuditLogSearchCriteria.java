package com.minupay.audit.domain;

import java.time.Instant;

public record AuditLogSearchCriteria(
        String eventType,
        String aggregateType,
        String aggregateId,
        String traceId,
        Instant occurredFrom,
        Instant occurredTo
) {}
