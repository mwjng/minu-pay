package com.minupay.payment.application;

import com.minupay.common.exception.ErrorCode;
import com.minupay.common.exception.MinuPayException;
import com.minupay.common.idempotency.IdempotencyService;
import com.minupay.payment.application.dto.PaymentCommand;
import com.minupay.payment.application.dto.PaymentInfo;
import com.minupay.payment.domain.Payment;
import com.minupay.payment.domain.PaymentRepository;
import com.minupay.payment.domain.PaymentStatus;
import com.minupay.payment.domain.PgProvider;
import com.minupay.payment.infrastructure.metrics.PaymentMetrics;
import com.minupay.payment.infrastructure.pg.PgApproveRequest;
import com.minupay.payment.infrastructure.pg.PgClient;
import com.minupay.payment.infrastructure.pg.PgResult;
import com.minupay.payment.infrastructure.pglog.PgPaymentLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentFacade {

    private final PaymentService paymentService;
    private final PaymentRepository paymentRepository;
    private final PgClient pgClient;
    private final PgPaymentLogService pgPaymentLogService;
    private final IdempotencyService idempotencyService;
    private final PaymentMetrics paymentMetrics;

    public PaymentInfo request(PaymentCommand command) {
        Optional<PaymentInfo> cached = idempotencyService.findCachedResponse(command.idempotencyKey(), PaymentInfo.class);
        if (cached.isPresent()) return cached.get();

        PaymentService.PaymentInitResult init = paymentService.initiate(command);

        long startTime = System.nanoTime();
        PgResult pgResult = null;
        try {
            pgResult = pgClient.approve(new PgApproveRequest(command.tossPaymentKey(), init.paymentId(), command.amount()));
        } finally {
            long durationNs = System.nanoTime() - startTime;
            long durationMs = TimeUnit.NANOSECONDS.toMillis(durationNs);
            paymentMetrics.recordPgApproveDuration(durationNs);
            pgPaymentLogService.save(
                    init.paymentId(), PgProvider.TOSS.name(), "APPROVE",
                    Map.of("paymentKey", command.tossPaymentKey(), "amount", command.amount()),
                    pgResult != null ? Map.of("success", pgResult.success()) : Map.of("error", "no_response"),
                    durationMs
            );
        }

        PaymentInfo result;
        if (pgResult != null && pgResult.success()) {
            result = paymentService.approve(init.paymentId(), init.walletTransactionId(), pgResult);
            paymentMetrics.recordApproved();
        } else {
            String reason = pgResult != null ? pgResult.errorMessage() : "PG response timeout";
            result = paymentService.fail(init.paymentId(), init.walletTransactionId(), command.userId(), command.amount(), reason);
            paymentMetrics.recordFailed();
        }

        idempotencyService.complete(command.idempotencyKey(), result);
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

        PaymentInfo cancelled = paymentService.confirmCancel(paymentId, payment.getUserId(), payment.getAmount(), pgResult);
        paymentMetrics.recordCancelled();
        return cancelled;
    }
}
