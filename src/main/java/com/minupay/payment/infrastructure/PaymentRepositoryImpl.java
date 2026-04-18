package com.minupay.payment.infrastructure;

import com.minupay.payment.domain.Payment;
import com.minupay.payment.domain.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class PaymentRepositoryImpl implements PaymentRepository {

    private final PaymentJpaRepository jpaRepository;
    private final PgPaymentJpaRepository pgPaymentJpaRepository;

    @Override
    public Payment save(Payment payment) {
        PaymentEntity saved = jpaRepository.save(PaymentEntity.from(payment));
        if (payment.getPgPayment() != null) {
            pgPaymentJpaRepository.save(PgPaymentEntity.from(payment.getId(), payment.getPgPayment()));
        }
        return saved.toDomain(pgPaymentJpaRepository);
    }

    @Override
    public Optional<Payment> findById(String id) {
        return jpaRepository.findById(id).map(e -> e.toDomain(pgPaymentJpaRepository));
    }

    @Override
    public Optional<Payment> findByIdempotencyKey(String idempotencyKey) {
        return jpaRepository.findByIdempotencyKey(idempotencyKey).map(e -> e.toDomain(pgPaymentJpaRepository));
    }
}
