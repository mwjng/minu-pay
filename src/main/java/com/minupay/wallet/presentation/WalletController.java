package com.minupay.wallet.presentation;

import com.minupay.auth.infrastructure.LoginUser;
import com.minupay.common.response.ApiResponse;
import com.minupay.wallet.application.WalletService;
import com.minupay.wallet.application.dto.WalletInfo;
import com.minupay.wallet.presentation.dto.ChargeRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Wallet", description = "지갑 생성/조회/충전")
@RestController
@RequestMapping("/api/wallets")
@RequiredArgsConstructor
public class WalletController {

    private final WalletService walletService;

    @Operation(summary = "지갑 생성", description = "현재 로그인한 사용자에게 지갑을 1개 발급한다.")
    @PostMapping
    public ResponseEntity<ApiResponse<WalletInfo>> createWallet(@AuthenticationPrincipal LoginUser loginUser) {
        return ResponseEntity.ok(ApiResponse.ok(walletService.createWallet(loginUser.getUserId())));
    }

    @Operation(summary = "내 지갑 조회", description = "현재 로그인한 사용자의 지갑 잔액을 반환한다.")
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<WalletInfo>> getMyWallet(@AuthenticationPrincipal LoginUser loginUser) {
        return ResponseEntity.ok(ApiResponse.ok(walletService.getWalletByUserId(loginUser.getUserId())));
    }

    @Operation(summary = "지갑 충전",
            description = "idempotencyKey 로 중복 실행을 막는다. 동일 키로 재호출하면 같은 결과를 반환한다.")
    @PostMapping("/me/charge")
    public ResponseEntity<ApiResponse<WalletInfo>> charge(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestBody @Valid ChargeRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.ok(walletService.charge(request.toCommand(loginUser.getUserId()))));
    }
}
