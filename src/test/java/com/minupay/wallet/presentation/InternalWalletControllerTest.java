package com.minupay.wallet.presentation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.minupay.common.exception.ErrorCode;
import com.minupay.common.exception.MinuPayException;
import com.minupay.wallet.application.WalletService;
import com.minupay.wallet.application.dto.ChargeCommand;
import com.minupay.wallet.application.dto.WalletInfo;
import com.minupay.wallet.domain.WalletStatus;
import com.minupay.wallet.presentation.dto.WalletTxRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@DisplayName("InternalWalletController — S2S 지갑 이체 API")
class InternalWalletControllerTest {

    private static final String API_KEY_HEADER = "X-Internal-Api-Key";
    private static final String IDEMPOTENCY_HEADER = "Idempotency-Key";

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @Value("${pay-service.internal-api-key}")
    String internalApiKey;

    @MockitoBean WalletService walletService;

    @Test
    @DisplayName("내부 API 키 없으면 403")
    void 내부키_없으면_403() throws Exception {
        WalletTxRequest body = new WalletTxRequest(BigDecimal.valueOf(500), "TRADING_DEPOSIT", "idem-1");

        mockMvc.perform(post("/api/internal/wallets/{userId}/deduct", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(IDEMPOTENCY_HEADER, "idem-1")
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("내부 API 키가 틀리면 403")
    void 내부키_틀리면_403() throws Exception {
        WalletTxRequest body = new WalletTxRequest(BigDecimal.valueOf(500), "TRADING_DEPOSIT", "idem-2");

        mockMvc.perform(post("/api/internal/wallets/{userId}/deduct", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(IDEMPOTENCY_HEADER, "idem-2")
                        .header(API_KEY_HEADER, "wrong-key")
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("차감 성공 시 200 과 잔액 반환")
    void 차감_성공() throws Exception {
        WalletTxRequest body = new WalletTxRequest(BigDecimal.valueOf(300), "TRADING_DEPOSIT", "idem-3");
        given(walletService.internalDeduct(any(ChargeCommand.class)))
                .willReturn(new WalletInfo(10L, 1L, 700L, WalletStatus.ACTIVE));

        mockMvc.perform(post("/api/internal/wallets/{userId}/deduct", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(IDEMPOTENCY_HEADER, "idem-3")
                        .header(API_KEY_HEADER, internalApiKey)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.walletId").value(10))
                .andExpect(jsonPath("$.data.amount").value(300))
                .andExpect(jsonPath("$.data.balanceAfter").value(700));
    }

    @Test
    @DisplayName("입금 성공 시 200 과 증가된 잔액 반환")
    void 입금_성공() throws Exception {
        WalletTxRequest body = new WalletTxRequest(BigDecimal.valueOf(500), "TRADING_WITHDRAW", "idem-4");
        given(walletService.internalCredit(any(ChargeCommand.class)))
                .willReturn(new WalletInfo(10L, 1L, 1500L, WalletStatus.ACTIVE));

        mockMvc.perform(post("/api/internal/wallets/{userId}/credit", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(IDEMPOTENCY_HEADER, "idem-4")
                        .header(API_KEY_HEADER, internalApiKey)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.balanceAfter").value(1500));
    }

    @Test
    @DisplayName("잔액 부족은 422")
    void 잔액부족_422() throws Exception {
        WalletTxRequest body = new WalletTxRequest(BigDecimal.valueOf(10_000), "TRADING_DEPOSIT", "idem-5");
        willThrow(new MinuPayException(ErrorCode.INSUFFICIENT_BALANCE))
                .given(walletService).internalDeduct(any(ChargeCommand.class));

        mockMvc.perform(post("/api/internal/wallets/{userId}/deduct", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(IDEMPOTENCY_HEADER, "idem-5")
                        .header(API_KEY_HEADER, internalApiKey)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    @DisplayName("중복 요청은 409")
    void 중복요청_409() throws Exception {
        WalletTxRequest body = new WalletTxRequest(BigDecimal.valueOf(300), "TRADING_DEPOSIT", "idem-6");
        willThrow(new MinuPayException(ErrorCode.DUPLICATE_REQUEST))
                .given(walletService).internalDeduct(any(ChargeCommand.class));

        mockMvc.perform(post("/api/internal/wallets/{userId}/deduct", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(IDEMPOTENCY_HEADER, "idem-6")
                        .header(API_KEY_HEADER, internalApiKey)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isConflict());
    }
}
