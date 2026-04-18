package com.minupay.payment.infrastructure.idempotency;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

@Entity
@Table(name = "idempotency_keys")
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class IdempotencyKeyEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String keyValue;

    @Column(columnDefinition = "TEXT")
    private String responseBody;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private IdempotencyStatus status;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    private Instant expiresAt;

    public static IdempotencyKeyEntity processing(String keyValue) {
        IdempotencyKeyEntity entity = new IdempotencyKeyEntity();
        entity.keyValue = keyValue;
        entity.status = IdempotencyStatus.PROCESSING;
        entity.expiresAt = Instant.now().plusSeconds(86400);
        return entity;
    }

    public void complete(String responseBody) {
        this.responseBody = responseBody;
        this.status = IdempotencyStatus.COMPLETED;
    }
}
