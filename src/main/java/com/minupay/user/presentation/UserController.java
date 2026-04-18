package com.minupay.user.presentation;

import com.minupay.auth.infrastructure.LoginUser;
import com.minupay.common.response.ApiResponse;
import com.minupay.user.application.UserService;
import com.minupay.user.application.dto.UserInfo;
import com.minupay.user.presentation.dto.RegisterRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<UserInfo>> register(@RequestBody @Valid RegisterRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(userService.register(request.toCommand())));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserInfo>> getMyInfo(@AuthenticationPrincipal LoginUser loginUser) {
        return ResponseEntity.ok(ApiResponse.ok(userService.findById(loginUser.getUserId())));
    }
}
