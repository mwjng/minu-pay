package com.minupay.payment.infrastructure.pglog;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class PgPaymentLogService {

    private final PgPaymentLogRepository repository;

    // MongoDB 저장 실패가 결제 트랜잭션에 영향을 주면 안 됨 → 실패 시 로그만 남기고 swallow
    public void save(String paymentId, String pgProvider, String requestType,
                     Map<String, Object> request, Map<String, Object> response, long durationMs) {
        try {
            repository.save(
                    PgPaymentLog.of(paymentId, pgProvider, requestType, request, response, durationMs)
            );
        } catch (Exception e) {
            log.error("Failed to save PG payment log for paymentId={}", paymentId, e);
        }
    }
}
