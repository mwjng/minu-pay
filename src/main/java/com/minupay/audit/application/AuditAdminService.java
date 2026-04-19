package com.minupay.audit.application;

import com.minupay.audit.application.dto.AuditLogPageView;
import com.minupay.audit.application.dto.AuditLogView;
import com.minupay.audit.domain.AuditLogRepository;
import com.minupay.audit.domain.AuditLogSearchCriteria;
import com.minupay.common.exception.ErrorCode;
import com.minupay.common.exception.MinuPayException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuditAdminService {

    private static final int MAX_PAGE_SIZE = 200;

    private final AuditLogRepository auditLogRepository;

    public AuditLogPageView search(AuditLogSearchCriteria criteria, int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        return AuditLogPageView.from(auditLogRepository.search(criteria, safePage, safeSize));
    }

    public AuditLogView findById(String id) {
        return auditLogRepository.findById(id)
                .map(AuditLogView::from)
                .orElseThrow(() -> new MinuPayException(ErrorCode.AUDIT_LOG_NOT_FOUND, "Audit log not found: " + id));
    }
}
