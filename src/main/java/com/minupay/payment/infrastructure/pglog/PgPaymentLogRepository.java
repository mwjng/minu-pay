package com.minupay.payment.infrastructure.pglog;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface PgPaymentLogRepository extends MongoRepository<PgPaymentLog, String> {}
