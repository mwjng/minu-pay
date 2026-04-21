package com.minupay.user.infrastructure;

import com.minupay.common.entity.BaseTimeEntity;
import com.minupay.user.User;
import com.minupay.user.UserRole;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "users", indexes = @Index(name = "idx_email", columnList = "email", unique = true))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserEntity extends BaseTimeEntity {

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

    public static UserEntity from(User user) {
        UserEntity entity = new UserEntity();
        entity.email = user.getEmail();
        entity.name = user.getName();
        entity.password = user.getEncodedPassword();
        entity.role = user.getRole();
        return entity;
    }

    public User toDomain() {
        return User.of(id, email, name, password, role, getCreatedAt());
    }
}
