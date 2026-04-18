package com.minupay.wallet.infrastructure;

import com.minupay.common.entity.BaseTimeEntity;
import com.minupay.common.money.Money;
import com.minupay.wallet.domain.Wallet;
import com.minupay.wallet.domain.WalletStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "wallets", indexes = @Index(name = "idx_user_id", columnList = "user_id", unique = true))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WalletEntity extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private Long userId;

    @Column(nullable = false)
    private Long balance;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private WalletStatus status;

    @Version
    private Long version;

    public static WalletEntity from(Wallet wallet) {
        WalletEntity entity = new WalletEntity();
        entity.id = wallet.getId();
        entity.userId = wallet.getUserId();
        entity.balance = wallet.getBalance().toLong();
        entity.status = wallet.getStatus();
        entity.version = wallet.getVersion();
        return entity;
    }

    public Wallet toDomain() {
        return Wallet.of(id, userId, Money.of(balance), status, version);
    }
}
