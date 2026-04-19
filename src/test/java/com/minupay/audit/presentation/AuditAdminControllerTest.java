package com.minupay.audit.presentation;

import com.minupay.audit.application.AuditAdminService;
import com.minupay.audit.application.dto.AuditLogPageView;
import com.minupay.audit.application.dto.AuditLogView;
import com.minupay.audit.domain.AuditLogSearchCriteria;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@DisplayName("AuditAdminController — ADMIN 전용 감사 로그 조회 API")
class AuditAdminControllerTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean AuditAdminService auditAdminService;

    @Test
    @DisplayName("인증 없이 호출하면 접근이 거부된다")
    void anonymous_isRejected() throws Exception {
        mockMvc.perform(get("/api/admin/audit-logs"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("ADMIN 이 아닌 사용자는 403 을 받는다")
    void nonAdmin_isForbidden() throws Exception {
        mockMvc.perform(get("/api/admin/audit-logs").with(user("user1").roles("USER")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("ADMIN 호출 시 쿼리 파라미터가 criteria 로 전파된다")
    void admin_search_propagatesFilters() throws Exception {
        given(auditAdminService.search(any(AuditLogSearchCriteria.class), anyInt(), anyInt()))
                .willReturn(new AuditLogPageView(List.of(), 0, 20, 0));

        mockMvc.perform(get("/api/admin/audit-logs")
                        .with(user("admin").roles("ADMIN"))
                        .param("eventType", "WalletCharged")
                        .param("aggregateType", "Wallet")
                        .param("aggregateId", "42")
                        .param("traceId", "trace-xyz")
                        .param("occurredFrom", "2026-04-19T00:00:00Z")
                        .param("occurredTo", "2026-04-20T00:00:00Z")
                        .param("page", "1")
                        .param("size", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.totalElements").value(0));

        ArgumentCaptor<AuditLogSearchCriteria> criteriaCaptor = ArgumentCaptor.forClass(AuditLogSearchCriteria.class);
        ArgumentCaptor<Integer> pageCaptor = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<Integer> sizeCaptor = ArgumentCaptor.forClass(Integer.class);
        verify(auditAdminService).search(criteriaCaptor.capture(), pageCaptor.capture(), sizeCaptor.capture());

        AuditLogSearchCriteria captured = criteriaCaptor.getValue();
        assertThat(captured.eventType()).isEqualTo("WalletCharged");
        assertThat(captured.aggregateType()).isEqualTo("Wallet");
        assertThat(captured.aggregateId()).isEqualTo("42");
        assertThat(captured.traceId()).isEqualTo("trace-xyz");
        assertThat(captured.occurredFrom()).isEqualTo(Instant.parse("2026-04-19T00:00:00Z"));
        assertThat(captured.occurredTo()).isEqualTo(Instant.parse("2026-04-20T00:00:00Z"));
        assertThat(pageCaptor.getValue()).isEqualTo(1);
        assertThat(sizeCaptor.getValue()).isEqualTo(50);
    }

    @Test
    @DisplayName("ADMIN 단건 조회 시 DTO 를 그대로 반환한다")
    void admin_findById_returnsView() throws Exception {
        AuditLogView view = new AuditLogView(
                "evt-1", "WalletCharged", "10", "Wallet",
                "trace-1", Instant.parse("2026-04-19T10:00:00Z"),
                Instant.parse("2026-04-19T10:00:00.100Z"), "{\"amount\":500}");
        given(auditAdminService.findById("doc-id-1")).willReturn(view);

        mockMvc.perform(get("/api/admin/audit-logs/doc-id-1").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.eventId").value("evt-1"))
                .andExpect(jsonPath("$.data.eventType").value("WalletCharged"))
                .andExpect(jsonPath("$.data.payload").value("{\"amount\":500}"));
    }
}
