package com.minupay.user.presentation;

import com.minupay.auth.infrastructure.LoginUser;
import com.minupay.common.response.ApiResponse;
import com.minupay.user.application.UserService;
import com.minupay.user.application.dto.UserInfo;
import com.minupay.user.presentation.dto.RegisterRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
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

@Tag(name = "User", description = "사용자 가입/조회")
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @Operation(summary = "회원 가입", description = "이메일/비밀번호로 신규 사용자를 생성한다.")
    @SecurityRequirements
    @PostMapping("/register")
    public ResponseEntity<ApiResponse<UserInfo>> register(@RequestBody @Valid RegisterRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(userService.register(request.toCommand())));
    }

    @Operation(summary = "내 정보 조회", description = "JWT 로 인증된 사용자의 프로필을 반환한다.")
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserInfo>> getMyInfo(@AuthenticationPrincipal LoginUser loginUser) {
        return ResponseEntity.ok(ApiResponse.ok(userService.findById(loginUser.getUserId())));
    }
}
