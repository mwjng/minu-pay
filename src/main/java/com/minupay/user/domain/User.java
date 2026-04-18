package com.minupay.user.domain;

import java.time.Instant;

public class User {

    private final Long id;
    private final String email;
    private final String name;
    private final String encodedPassword;
    private final UserRole role;
    private final Instant createdAt;

    private User(Long id, String email, String name, String encodedPassword, UserRole role, Instant createdAt) {
        this.id = id;
        this.email = email;
        this.name = name;
        this.encodedPassword = encodedPassword;
        this.role = role;
        this.createdAt = createdAt;
    }

    public static User create(String email, String name, String encodedPassword) {
        return new User(null, email, name, encodedPassword, UserRole.USER, Instant.now());
    }

    public static User of(Long id, String email, String name, String encodedPassword, UserRole role, Instant createdAt) {
        return new User(id, email, name, encodedPassword, role, createdAt);
    }

    public Long getId() { return id; }
    public String getEmail() { return email; }
    public String getName() { return name; }
    public String getEncodedPassword() { return encodedPassword; }
    public UserRole getRole() { return role; }
    public Instant getCreatedAt() { return createdAt; }
}
