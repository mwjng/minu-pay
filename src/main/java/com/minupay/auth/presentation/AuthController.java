package com.minupay.auth.presentation;

import com.minupay.auth.application.AuthService;
import com.minupay.auth.application.dto.TokenInfo;
import com.minupay.auth.presentation.dto.LoginRequest;
import com.minupay.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Auth", description = "로그인/토큰 발급")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @Operation(summary = "로그인", description = "이메일/비밀번호로 JWT 토큰을 발급한다.")
    @SecurityRequirements
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<TokenInfo>> login(@RequestBody @Valid LoginRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(authService.login(request.toCommand())));
    }
}
