package com.minupay.user.application.dto;

import com.minupay.user.domain.User;
import com.minupay.user.domain.UserRole;

public record UserInfo(Long id, String email, String name, UserRole role) {

    public static UserInfo from(User user) {
        return new UserInfo(user.getId(), user.getEmail(), user.getName(), user.getRole());
    }
}
