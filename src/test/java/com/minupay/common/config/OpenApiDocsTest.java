package com.minupay.common.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@DisplayName("OpenAPI 문서가 정상 직렬화되어 노출된다")
class OpenApiDocsTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @Test
    @DisplayName("/v3/api-docs 호출 시 주요 엔드포인트가 모두 포함된다")
    void api_docs_expose_all_endpoints() throws Exception {
        String body = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode root = objectMapper.readTree(body);
        JsonNode paths = root.path("paths");

        assertThat(paths.has("/api/auth/login")).isTrue();
        assertThat(paths.has("/api/users/register")).isTrue();
        assertThat(paths.has("/api/wallets/me/charge")).isTrue();
        assertThat(paths.has("/api/payments")).isTrue();
        assertThat(root.path("components").path("securitySchemes").has("bearer-jwt")).isTrue();
    }
}
