package com.minupay.payment.presentation;

import com.minupay.auth.infrastructure.LoginUser;
import com.minupay.common.response.ApiResponse;
import com.minupay.payment.application.PaymentFacade;
import com.minupay.payment.application.dto.PaymentInfo;
import com.minupay.payment.presentation.dto.CreatePaymentRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentFacade paymentFacade;

    @PostMapping
    public ResponseEntity<ApiResponse<PaymentInfo>> createPayment(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestBody @Valid CreatePaymentRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.ok(paymentFacade.request(request.toCommand(loginUser.getUserId()))));
    }

    @PostMapping("/{paymentId}/cancel")
    public ResponseEntity<ApiResponse<PaymentInfo>> cancelPayment(
            @PathVariable String paymentId,
            @RequestParam(defaultValue = "사용자 요청") String reason
    ) {
        return ResponseEntity.ok(ApiResponse.ok(paymentFacade.cancel(paymentId, reason)));
    }
}
