package com.minupay.payment.presentation;

import com.minupay.auth.infrastructure.LoginUser;
import com.minupay.common.response.ApiResponse;
import com.minupay.payment.application.PaymentFacade;
import com.minupay.payment.application.dto.PaymentInfo;
import com.minupay.payment.presentation.dto.CreatePaymentRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Payment", description = "결제 요청/취소")
@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentFacade paymentFacade;

    @Operation(summary = "결제 요청",
            description = "지갑 잔액을 차감한 뒤 PG(토스페이먼츠)로 승인 요청한다. idempotencyKey 필수.")
    @PostMapping
    public ResponseEntity<ApiResponse<PaymentInfo>> createPayment(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestBody @Valid CreatePaymentRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.ok(paymentFacade.request(request.toCommand(loginUser.getUserId()))));
    }

    @Operation(summary = "결제 취소",
            description = "PG 취소 호출 후 지갑에 환불을 반영한다. Idempotency-Key 헤더 필수.")
    @PostMapping("/{paymentId}/cancel")
    public ResponseEntity<ApiResponse<PaymentInfo>> cancelPayment(
            @PathVariable String paymentId,
            @RequestParam(defaultValue = "사용자 요청") String reason,
            @RequestHeader("Idempotency-Key") String idempotencyKey
    ) {
        return ResponseEntity.ok(ApiResponse.ok(paymentFacade.cancel(paymentId, reason, idempotencyKey)));
    }
}
