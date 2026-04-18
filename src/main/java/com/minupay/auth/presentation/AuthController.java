package com.minupay.auth.presentation;

import com.minupay.auth.application.AuthService;
import com.minupay.auth.application.dto.TokenInfo;
import com.minupay.auth.presentation.dto.LoginRequest;
import com.minupay.common.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<TokenInfo>> login(@RequestBody @Valid LoginRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(authService.login(request.toCommand())));
    }
}
