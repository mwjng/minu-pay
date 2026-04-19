package com.minupay.audit.infrastructure;

import com.minupay.audit.domain.AuditLog;
import com.minupay.audit.domain.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

@Slf4j
@Repository
@RequiredArgsConstructor
public class AuditLogRepositoryImpl implements AuditLogRepository {

    private final AuditLogMongoRepository mongoRepository;

    @Override
    public boolean saveIfAbsent(AuditLog auditLog) {
        try {
            mongoRepository.save(AuditLogDocument.from(auditLog));
            return true;
        } catch (DuplicateKeyException e) {
            log.debug("Audit log already exists eventId={}", auditLog.getEventId());
            return false;
        }
    }
}
