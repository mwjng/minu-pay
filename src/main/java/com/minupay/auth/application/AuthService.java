package com.minupay.auth.application;

import com.minupay.auth.application.dto.LoginCommand;
import com.minupay.auth.application.dto.TokenInfo;
import com.minupay.auth.infrastructure.JwtProvider;
import com.minupay.common.exception.ErrorCode;
import com.minupay.common.exception.MinuPayException;
import com.minupay.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;

    @Transactional(readOnly = true)
    public TokenInfo login(LoginCommand command) {
        var user = userRepository.findByEmail(command.email())
                .orElseThrow(() -> new MinuPayException(ErrorCode.UNAUTHORIZED, "Invalid email or password"));

        if (!passwordEncoder.matches(command.password(), user.getEncodedPassword())) {
            throw new MinuPayException(ErrorCode.UNAUTHORIZED, "Invalid email or password");
        }

        return TokenInfo.of(jwtProvider.generate(user.getId(), user.getRole().name()));
    }
}
