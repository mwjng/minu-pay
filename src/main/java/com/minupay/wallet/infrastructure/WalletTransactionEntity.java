package com.minupay.wallet.infrastructure;

import com.minupay.common.money.Money;
import com.minupay.wallet.domain.WalletTransaction;
import com.minupay.wallet.domain.WalletTransactionType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
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
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

@Entity
@Table(
        name = "wallet_transactions",
        indexes = @Index(name = "idx_wallet_created", columnList = "wallet_id, created_at")
)
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WalletTransactionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long walletId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private WalletTransactionType type;

    @Column(nullable = false)
    private Long amount;

    @Column(nullable = false)
    private String referenceId;

    @Column(nullable = false)
    private String referenceType;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    public static WalletTransactionEntity from(WalletTransaction tx) {
        WalletTransactionEntity entity = new WalletTransactionEntity();
        entity.walletId = tx.getWalletId();
        entity.type = tx.getType();
        entity.amount = tx.getAmount().toLong();
        entity.referenceId = tx.getReferenceId();
        entity.referenceType = tx.getReferenceType();
        return entity;
    }

    public WalletTransaction toDomain() {
        return WalletTransaction.of(id, walletId, type, Money.of(amount), referenceId, referenceType);
    }
}
