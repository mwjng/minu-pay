package com.minupay.settlement.infrastructure;

import com.minupay.settlement.domain.SettlementItem;
import com.minupay.settlement.domain.SettlementItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class SettlementItemRepositoryImpl implements SettlementItemRepository {

    private final SettlementItemJpaRepository jpaRepository;

    @Override
    public SettlementItem save(SettlementItem item) {
        SettlementItemEntity saved = jpaRepository.save(SettlementItemEntity.from(item));
        return saved.toDomain();
    }

    @Override
    public Optional<SettlementItem> findByPaymentId(String paymentId) {
        return jpaRepository.findByPaymentId(paymentId).map(SettlementItemEntity::toDomain);
    }
}
