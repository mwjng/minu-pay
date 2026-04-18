package com.minupay.payment.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.minupay.common.exception.ErrorCode;
import com.minupay.common.exception.MinuPayException;
import com.minupay.common.money.Money;
import com.minupay.common.outbox.Outbox;
import com.minupay.common.outbox.OutboxRepository;
import com.minupay.payment.application.dto.PaymentCommand;
import com.minupay.payment.application.dto.PaymentInfo;
import com.minupay.payment.domain.Payment;
import com.minupay.payment.domain.PgPayment;
import com.minupay.payment.domain.PgProvider;
import com.minupay.payment.domain.PaymentRepository;
import com.minupay.payment.infrastructure.idempotency.IdempotencyKeyEntity;
import com.minupay.payment.infrastructure.idempotency.IdempotencyKeyJpaRepository;
import com.minupay.payment.infrastructure.pg.PgApproveRequest;
import com.minupay.payment.infrastructure.pg.PgClient;
import com.minupay.payment.infrastructure.pg.PgResult;
import com.minupay.payment.infrastructure.pglog.PgPaymentLogService;
import com.minupay.wallet.application.WalletService;
import com.minupay.wallet.application.dto.ChargeCommand;
import com.minupay.wallet.application.dto.WalletDeductResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentFacade {

    private final PaymentRepository paymentRepository;
    private final WalletService walletService;
    private final PgClient pgClient;
    private final PgPaymentLogService pgPaymentLogService;
    private final IdempotencyKeyJpaRepository idempotencyKeyRepository;
    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    // 트랜잭션 없음 — 내부 단계별로 트랜잭션 분리
    public PaymentInfo request(PaymentCommand command) {
        // 1. 멱등성 체크 — 이미 완료된 요청이면 캐시된 응답 반환
        Optional<IdempotencyKeyEntity> existing = idempotencyKeyRepository.findByKeyValue(command.idempotencyKey());
        if (existing.isPresent() && existing.get().getResponseBody() != null) {
            try {
                return objectMapper.readValue(existing.get().getResponseBody(), PaymentInfo.class);
            } catch (JsonProcessingException e) {
                throw new MinuPayException(ErrorCode.INTERNAL_ERROR);
            }
        }

        // 2. PROCESSING 상태로 멱등키 저장 + Payment 생성 + 지갑 선차감 (한 트랜잭션)
        PaymentInitResult init = initiate(command);

        // 3. PG 승인 요청 (트랜잭션 외부)
        long startTime = System.currentTimeMillis();
        PgResult pgResult = null;
        try {
            pgResult = pgClient.approve(new PgApproveRequest(command.tossPaymentKey(), init.paymentId(), command.amount()));
        } finally {
            // PG 로그 동기 저장 (성공/실패 무관, 실패해도 swallow)
            long duration = System.currentTimeMillis() - startTime;
            pgPaymentLogService.save(
                    init.paymentId(), PgProvider.TOSS.name(), "APPROVE",
                    Map.of("paymentKey", command.tossPaymentKey(), "amount", command.amount()),
                    pgResult != null ? Map.of("success", pgResult.success()) : Map.of("error", "no_response"),
                    duration
            );
        }

        // 4. PG 결과에 따라 승인 or 실패 처리
        PaymentInfo result;
        if (pgResult != null && pgResult.success()) {
            result = approve(init.paymentId(), init.walletTransactionId(), pgResult);
        } else {
            String reason = pgResult != null ? pgResult.errorMessage() : "PG response timeout";
            result = fail(init.paymentId(), init.walletTransactionId(), command.userId(), command.amount(), reason);
        }

        // 5. 멱등키 완료 처리
        completeIdempotencyKey(command.idempotencyKey(), result);
        return result;
    }

    public PaymentInfo cancel(String paymentId, String reason) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new MinuPayException(ErrorCode.PAYMENT_NOT_FOUND));

        if (payment.getStatus() != com.minupay.payment.domain.PaymentStatus.APPROVED) {
            throw new MinuPayException(ErrorCode.INVALID_PAYMENT_STATUS, "Only APPROVED payment can be cancelled");
        }

        String pgTxId = payment.getPgPayment().getPgTxId();

        // PG 취소 (트랜잭션 외부)
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

        return confirmCancel(paymentId, payment.getUserId(), payment.getAmount(), pgResult);
    }

    @Transactional
    protected PaymentInitResult initiate(PaymentCommand command) {
        // 멱등키 선점 (unique constraint로 중복 방지)
        idempotencyKeyRepository.save(IdempotencyKeyEntity.processing(command.idempotencyKey()));

        Payment payment = Payment.create(command.userId(), command.merchantId(), Money.of(command.amount()), command.idempotencyKey());
        paymentRepository.save(payment);

        WalletDeductResult deductResult = walletService.deductForPayment(
                new ChargeCommand(command.userId(), Money.of(command.amount()), payment.getId(), "PAYMENT")
        );

        return new PaymentInitResult(payment.getId(), deductResult.transactionId());
    }

    @Transactional
    protected PaymentInfo approve(String paymentId, Long walletTransactionId, PgResult pgResult) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new MinuPayException(ErrorCode.PAYMENT_NOT_FOUND));

        PgPayment pgPayment = PgPayment.approved(PgProvider.TOSS, pgResult.pgTxId(), null);
        payment.approve(walletTransactionId, pgPayment);

        Payment saved = paymentRepository.save(payment);
        publishEvents(payment);
        return PaymentInfo.from(saved);
    }

    @Transactional
    protected PaymentInfo fail(String paymentId, Long walletTransactionId, Long userId, long amount, String reason) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new MinuPayException(ErrorCode.PAYMENT_NOT_FOUND));

        payment.fail(reason);
        paymentRepository.save(payment);

        // 지갑 환불
        walletService.refund(new ChargeCommand(userId, Money.of(amount), paymentId, "PAYMENT_FAIL_REFUND"));
        publishEvents(payment);
        return PaymentInfo.from(payment);
    }

    @Transactional
    protected PaymentInfo confirmCancel(String paymentId, Long userId, Money amount, PgResult pgResult) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new MinuPayException(ErrorCode.PAYMENT_NOT_FOUND));

        PgPayment cancelPgPayment = PgPayment.cancelled(PgProvider.TOSS, pgResult.pgTxId(), null);
        payment.cancel(cancelPgPayment);
        paymentRepository.save(payment);

        // 지갑 환불
        walletService.refund(new ChargeCommand(userId, amount, paymentId, "PAYMENT_CANCEL_REFUND"));
        publishEvents(payment);
        return PaymentInfo.from(payment);
    }

    private void publishEvents(Payment payment) {
        payment.getDomainEvents().forEach(event -> {
            try {
                String payload = objectMapper.writeValueAsString(event.getPayload());
                String topic = switch (event.getEventType()) {
                    case "PaymentApproved" -> "payment.approved";
                    case "PaymentFailed" -> "payment.failed";
                    case "PaymentCancelled" -> "payment.cancelled";
                    default -> "payment.events";
                };
                outboxRepository.save(Outbox.create(
                        event.getAggregateId(), event.getAggregateType(),
                        event.getEventType(), topic, event.getAggregateId(), payload
                ));
            } catch (JsonProcessingException e) {
                throw new MinuPayException(ErrorCode.INTERNAL_ERROR, "Event serialization failed");
            }
        });
        payment.clearDomainEvents();
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

    private record PaymentInitResult(String paymentId, Long walletTransactionId) {}
}
