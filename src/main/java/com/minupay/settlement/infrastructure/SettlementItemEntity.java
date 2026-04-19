package com.minupay.settlement.infrastructure;

import com.minupay.common.entity.BaseTimeEntity;
import com.minupay.common.money.Money;
import com.minupay.settlement.domain.SettlementItem;
import com.minupay.settlement.domain.SettlementItemStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(
        name = "settlement_items",
        indexes = {
                @Index(name = "idx_payment_id", columnList = "payment_id", unique = true),
                @Index(name = "idx_merchant_date", columnList = "merchant_id, target_date")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SettlementItemEntity extends BaseTimeEntity {

    @Id
    private String id;

    @Column(nullable = false, unique = true)
    private String paymentId;

    @Column(nullable = false)
    private String merchantId;

    @Column(nullable = false)
    private LocalDate targetDate;

    @Column(nullable = false)
    private Long grossAmount;

    @Column(nullable = false)
    private Long fee;

    @Column(nullable = false)
    private Long netAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SettlementItemStatus status;

    private Instant cancelledAt;

    public static SettlementItemEntity from(SettlementItem item) {
        SettlementItemEntity entity = new SettlementItemEntity();
        entity.id = item.getId();
        entity.paymentId = item.getPaymentId();
        entity.merchantId = item.getMerchantId();
        entity.targetDate = item.getTargetDate();
        entity.grossAmount = item.getGrossAmount().toLong();
        entity.fee = item.getFee().toLong();
        entity.netAmount = item.getNetAmount().toLong();
        entity.status = item.getStatus();
        entity.cancelledAt = item.getCancelledAt();
        return entity;
    }

    public SettlementItem toDomain() {
        return SettlementItem.of(
                id, paymentId, merchantId, targetDate,
                Money.of(grossAmount), Money.of(fee), Money.of(netAmount),
                status, cancelledAt
        );
    }
}
