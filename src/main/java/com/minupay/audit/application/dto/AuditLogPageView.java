package com.minupay.audit.application.dto;

import com.minupay.audit.domain.AuditLogPage;

import java.util.List;

public record AuditLogPageView(
        List<AuditLogView> content,
        int page,
        int size,
        long totalElements
) {
    public static AuditLogPageView from(AuditLogPage page) {
        return new AuditLogPageView(
                page.content().stream().map(AuditLogView::from).toList(),
                page.page(),
                page.size(),
                page.totalElements()
        );
    }
}
