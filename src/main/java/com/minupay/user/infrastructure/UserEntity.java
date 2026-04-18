package com.minupay.user.infrastructure;

import com.minupay.user.domain.User;
import com.minupay.user.domain.UserRole;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "users", indexes = @Index(name = "idx_email", columnList = "email", unique = true))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String password;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserRole role;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    public static UserEntity from(User user) {
        UserEntity entity = new UserEntity();
        entity.id = user.getId();
        entity.email = user.getEmail();
        entity.name = user.getName();
        entity.password = user.getEncodedPassword();
        entity.role = user.getRole();
        entity.createdAt = user.getCreatedAt();
        return entity;
    }

    public User toDomain() {
        return User.of(id, email, name, password, role, createdAt);
    }
}
