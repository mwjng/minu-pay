package com.minupay.wallet.presentation;

import com.minupay.auth.infrastructure.LoginUser;
import com.minupay.common.response.ApiResponse;
import com.minupay.wallet.application.WalletService;
import com.minupay.wallet.application.dto.WalletInfo;
import com.minupay.wallet.presentation.dto.ChargeRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/wallets")
@RequiredArgsConstructor
public class WalletController {

    private final WalletService walletService;

    @PostMapping
    public ResponseEntity<ApiResponse<WalletInfo>> createWallet(@AuthenticationPrincipal LoginUser loginUser) {
        return ResponseEntity.ok(ApiResponse.ok(walletService.createWallet(loginUser.getUserId())));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<WalletInfo>> getMyWallet(@AuthenticationPrincipal LoginUser loginUser) {
        return ResponseEntity.ok(ApiResponse.ok(walletService.getWalletByUserId(loginUser.getUserId())));
    }

    @PostMapping("/me/charge")
    public ResponseEntity<ApiResponse<WalletInfo>> charge(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestBody @Valid ChargeRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.ok(walletService.charge(request.toCommand(loginUser.getUserId()))));
    }
}
