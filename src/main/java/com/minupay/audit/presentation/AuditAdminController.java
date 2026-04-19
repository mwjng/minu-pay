package com.minupay.audit.presentation;

import com.minupay.audit.application.AuditAdminService;
import com.minupay.audit.application.dto.AuditLogPageView;
import com.minupay.audit.application.dto.AuditLogView;
import com.minupay.audit.domain.AuditLogSearchCriteria;
import com.minupay.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;

@Tag(name = "Admin - Audit", description = "감사 로그 관리 (ADMIN 권한)")
@RestController
@RequestMapping("/api/admin/audit-logs")
@RequiredArgsConstructor
public class AuditAdminController {

    private final AuditAdminService auditAdminService;

    @Operation(summary = "감사 로그 검색", description = "필터 조건으로 감사 로그를 페이지로 조회한다. 최신 기록순.")
    @GetMapping
    public ResponseEntity<ApiResponse<AuditLogPageView>> search(
            @Parameter(description = "이벤트 타입 (예: WalletCharged)")
            @RequestParam(required = false) String eventType,
            @Parameter(description = "집계 타입 (예: Wallet, Payment)")
            @RequestParam(required = false) String aggregateType,
            @Parameter(description = "집계 ID")
            @RequestParam(required = false) String aggregateId,
            @Parameter(description = "추적 ID")
            @RequestParam(required = false) String traceId,
            @Parameter(description = "발생 시각 시작 (ISO-8601, 포함)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant occurredFrom,
            @Parameter(description = "발생 시각 종료 (ISO-8601, 미포함)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant occurredTo,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        AuditLogSearchCriteria criteria = new AuditLogSearchCriteria(
                eventType, aggregateType, aggregateId, traceId, occurredFrom, occurredTo);
        return ResponseEntity.ok(ApiResponse.ok(auditAdminService.search(criteria, page, size)));
    }

    @Operation(summary = "감사 로그 단건 조회", description = "MongoDB document id 로 단건 조회한다.")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<AuditLogView>> findById(@PathVariable String id) {
        return ResponseEntity.ok(ApiResponse.ok(auditAdminService.findById(id)));
    }
}
