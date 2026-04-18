package com.minupay.payment.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

interface PaymentJpaRepository extends JpaRepository<PaymentEntity, String> {
    Optional<PaymentEntity> findByIdempotencyKey(String idempotencyKey);
}
