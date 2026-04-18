package com.minupay.auth.infrastructure;

import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;

import java.util.List;

public class LoginUser extends User {

    private final Long userId;

    public LoginUser(Long userId, String role) {
        super(userId.toString(), "", List.of(new SimpleGrantedAuthority("ROLE_" + role)));
        this.userId = userId;
    }

    public Long getUserId() {
        return userId;
    }
}
