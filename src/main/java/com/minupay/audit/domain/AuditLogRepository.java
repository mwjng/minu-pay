package com.minupay.audit.domain;

import java.util.Optional;

public interface AuditLogRepository {

    /**
     * @return true if newly inserted, false if eventId already existed (duplicate skipped).
     */
    boolean saveIfAbsent(AuditLog auditLog);

    Optional<AuditLog> findById(String id);

    AuditLogPage search(AuditLogSearchCriteria criteria, int page, int size);
}
