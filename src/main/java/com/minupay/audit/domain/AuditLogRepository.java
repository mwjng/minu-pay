package com.minupay.audit.domain;

public interface AuditLogRepository {

    /**
     * @return true if newly inserted, false if eventId already existed (duplicate skipped).
     */
    boolean saveIfAbsent(AuditLog auditLog);
}
