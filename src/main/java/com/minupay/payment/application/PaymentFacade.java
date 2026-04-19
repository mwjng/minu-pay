package com.minupay.payment.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.minupay.common.exception.ErrorCode;
import com.minupay.common.exception.MinuPayException;
import com.minupay.payment.application.dto.PaymentCommand;
import com.minupay.payment.application.dto.PaymentInfo;
import com.minupay.payment.domain.Payment;
import com.minupay.payment.domain.PaymentRepository;
import com.minupay.payment.domain.PaymentStatus;
import com.minupay.payment.domain.PgProvider;
import com.minupay.payment.infrastructure.idempotency.IdempotencyKeyEntity;
import com.minupay.payment.infrastructure.idempotency.IdempotencyKeyJpaRepository;
import com.minupay.payment.infrastructure.pg.PgApproveRequest;
import com.minupay.payment.infrastructure.pg.PgClient;
import com.minupay.payment.infrastructure.pg.PgResult;
import com.minupay.payment.infrastructure.pglog.PgPaymentLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentFacade {

    private final PaymentService paymentService;
    private final PaymentRepository paymentRepository;
    private final PgClient pgClient;
    private final PgPaymentLogService pgPaymentLogService;
    private final IdempotencyKeyJpaRepository idempotencyKeyRepository;
    private final ObjectMapper objectMapper;

    public PaymentInfo request(PaymentCommand command) {
        Optional<IdempotencyKeyEntity> existing = idempotencyKeyRepository.findByKeyValue(command.idempotencyKey());
        if (existing.isPresent() && existing.get().getResponseBody() != null) {
            try {
                return objectMapper.readValue(existing.get().getResponseBody(), PaymentInfo.class);
            } catch (JsonProcessingException e) {
                throw new MinuPayException(ErrorCode.INTERNAL_ERROR);
            }
        }

        PaymentService.PaymentInitResult init = paymentService.initiate(command);

        long startTime = System.currentTimeMillis();
        PgResult pgResult = null;
        try {
            pgResult = pgClient.approve(new PgApproveRequest(command.tossPaymentKey(), init.paymentId(), command.amount()));
        } finally {
            long duration = System.currentTimeMillis() - startTime;
            pgPaymentLogService.save(
                    init.paymentId(), PgProvider.TOSS.name(), "APPROVE",
                    Map.of("paymentKey", command.tossPaymentKey(), "amount", command.amount()),
                    pgResult != null ? Map.of("success", pgResult.success()) : Map.of("error", "no_response"),
                    duration
            );
        }

        PaymentInfo result;
        if (pgResult != null && pgResult.success()) {
            result = paymentService.approve(init.paymentId(), init.walletTransactionId(), pgResult);
        } else {
            String reason = pgResult != null ? pgResult.errorMessage() : "PG response timeout";
            result = paymentService.fail(init.paymentId(), init.walletTransactionId(), command.userId(), command.amount(), reason);
        }

        completeIdempotencyKey(command.idempotencyKey(), result);
        return result;
    }

    public PaymentInfo cancel(String paymentId, String reason) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new MinuPayException(ErrorCode.PAYMENT_NOT_FOUND));

        if (payment.getStatus() != PaymentStatus.APPROVED) {
            throw new MinuPayException(ErrorCode.INVALID_PAYMENT_STATUS, "Only APPROVED payment can be cancelled");
        }

        String pgTxId = payment.getPgPayment().getPgTxId();

        long startTime = System.currentTimeMillis();
        PgResult pgResult = null;
        try {
            pgResult = pgClient.cancel(pgTxId, reason);
        } finally {
            long duration = System.currentTimeMillis() - startTime;
            pgPaymentLogService.save(
                    paymentId, PgProvider.TOSS.name(), "CANCEL",
                    Map.of("pgTxId", pgTxId, "reason", reason),
                    pgResult != null ? Map.of("success", pgResult.success()) : Map.of("error", "no_response"),
                    duration
            );
        }

        if (pgResult == null || !pgResult.success()) {
            throw new MinuPayException(ErrorCode.PG_APPROVAL_FAILED, "PG cancel failed");
        }

        return paymentService.confirmCancel(paymentId, payment.getUserId(), payment.getAmount(), pgResult);
    }

    private void completeIdempotencyKey(String keyValue, PaymentInfo result) {
        idempotencyKeyRepository.findByKeyValue(keyValue).ifPresent(key -> {
            try {
                key.complete(objectMapper.writeValueAsString(result));
                idempotencyKeyRepository.save(key);
            } catch (JsonProcessingException e) {
                log.error("Failed to serialize idempotency response for key={}", keyValue, e);
            }
        });
    }
}
