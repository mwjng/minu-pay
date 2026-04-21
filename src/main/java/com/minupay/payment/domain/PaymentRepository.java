package com.minupay.payment.domain;

import java.util.Optional;

public interface PaymentRepository {
    Payment save(Payment payment);
    Optional<Payment> findById(String id);
    Optional<Payment> findByIdWithLock(String id);
    Optional<Payment> findByIdempotencyKey(String idempotencyKey);
}
