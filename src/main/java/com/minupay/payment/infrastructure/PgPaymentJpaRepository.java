package com.minupay.payment.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

interface PgPaymentJpaRepository extends JpaRepository<PgPaymentEntity, Long> {
    Optional<PgPaymentEntity> findTopByPaymentIdOrderByCreatedAtDesc(String paymentId);
}
