package com.minupay.user.application;

import com.minupay.common.exception.ErrorCode;
import com.minupay.common.exception.MinuPayException;
import com.minupay.user.application.dto.RegisterCommand;
import com.minupay.user.application.dto.UserInfo;
import com.minupay.user.User;
import com.minupay.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public UserInfo register(RegisterCommand command) {
        if (userRepository.existsByEmail(command.email())) {
            throw new MinuPayException(ErrorCode.DUPLICATE_REQUEST, "Email already in use");
        }
        User user = User.create(command.email(), command.name(), passwordEncoder.encode(command.password()));
        return UserInfo.from(userRepository.save(user));
    }

    @Transactional(readOnly = true)
    public UserInfo findById(Long id) {
        return userRepository.findById(id)
                .map(UserInfo::from)
                .orElseThrow(() -> new MinuPayException(ErrorCode.INVALID_INPUT, "User not found"));
    }

    @Transactional(readOnly = true)
    public UserInfo findByEmail(String email) {
        return userRepository.findByEmail(email)
                .map(UserInfo::from)
                .orElseThrow(() -> new MinuPayException(ErrorCode.INVALID_INPUT, "User not found"));
    }
}
