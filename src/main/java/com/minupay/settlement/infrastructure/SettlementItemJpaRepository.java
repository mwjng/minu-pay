package com.minupay.settlement.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

interface SettlementItemJpaRepository extends JpaRepository<SettlementItemEntity, String> {
    Optional<SettlementItemEntity> findByPaymentId(String paymentId);
}
