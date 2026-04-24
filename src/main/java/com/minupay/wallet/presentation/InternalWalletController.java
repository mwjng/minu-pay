package com.minupay.wallet.presentation;

import com.minupay.common.response.ApiResponse;
import com.minupay.wallet.application.WalletService;
import com.minupay.wallet.application.dto.WalletInfo;
import com.minupay.wallet.presentation.dto.WalletTxRequest;
import com.minupay.wallet.presentation.dto.WalletTxResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Internal Wallet", description = "S2S 전용: minu-trade 등 내부 서비스의 지갑 이체 API")
@RestController
@RequestMapping("/api/internal/wallets")
@RequiredArgsConstructor
public class InternalWalletController {

    private final WalletService walletService;

    @Operation(summary = "지갑 차감 (S2S)",
            description = "minu-trade 등 내부 서비스가 증권계좌 입금을 위해 사용자 지갑에서 금액을 차감한다. idempotencyKey 로 중복 실행을 막는다.")
    @PostMapping("/{userId}/deduct")
    public ResponseEntity<ApiResponse<WalletTxResponse>> deduct(
            @PathVariable Long userId,
            @RequestBody @Valid WalletTxRequest request
    ) {
        WalletInfo info = walletService.internalDeduct(request.toCommand(userId));
        return ResponseEntity.ok(ApiResponse.ok(WalletTxResponse.of(info, request.amount())));
    }

    @Operation(summary = "지갑 입금 (S2S)",
            description = "minu-trade 등 내부 서비스가 증권계좌 출금/매도 대금 등을 위해 사용자 지갑에 금액을 입금한다. idempotencyKey 로 중복 실행을 막는다.")
    @PostMapping("/{userId}/credit")
    public ResponseEntity<ApiResponse<WalletTxResponse>> credit(
            @PathVariable Long userId,
            @RequestBody @Valid WalletTxRequest request
    ) {
        WalletInfo info = walletService.internalCredit(request.toCommand(userId));
        return ResponseEntity.ok(ApiResponse.ok(WalletTxResponse.of(info, request.amount())));
    }
}
