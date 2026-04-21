package com.minupay.payment.infrastructure;

import com.minupay.payment.domain.PgPayment;
import com.minupay.payment.domain.PgProvider;
import com.minupay.payment.domain.PgStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

@Entity
@Table(name = "pg_payments")
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PgPaymentEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String paymentId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PgProvider pgProvider;

    @Column(nullable = false)
    private String pgTxId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PgStatus status;

    private String pgLogId;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    public static PgPaymentEntity from(String paymentId, PgPayment pgPayment) {
        PgPaymentEntity entity = new PgPaymentEntity();
        entity.paymentId = paymentId;
        entity.pgProvider = pgPayment.getPgProvider();
        entity.pgTxId = pgPayment.getPgTxId();
        entity.status = pgPayment.getStatus();
        entity.pgLogId = pgPayment.getPgLogId();
        return entity;
    }

    public PgPayment toDomain() {
        return PgPayment.of(id, pgProvider, pgTxId, status, pgLogId);
    }
}
