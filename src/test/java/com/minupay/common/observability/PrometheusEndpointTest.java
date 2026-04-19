package com.minupay.common.observability;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@DisplayName("/actuator/prometheus — 트래픽/도메인 메트릭이 노출된다")
class PrometheusEndpointTest {

    @Autowired MockMvc mockMvc;

    @Test
    @DisplayName("엔드포인트 호출 후 http_server_requests 와 커스텀 메트릭이 존재한다")
    void prometheus_exposes_metrics() throws Exception {
        mockMvc.perform(get("/api/auth/login")
                        .contentType("application/json")
                        .content("{\"email\":\"nobody@test\",\"password\":\"x\"}"));

        MvcResult result = mockMvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isOk())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body).contains("http_server_requests_seconds");
        assertThat(body).contains("minupay_wallet_charges_total");
        assertThat(body).contains("minupay_payment_requests_total");
        assertThat(body).contains("minupay_pg_approve_seconds");
        assertThat(body).contains("application=\"minu-pay\"");
    }
}
