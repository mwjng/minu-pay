package com.minupay.payment.application;

import com.minupay.common.exception.ErrorCode;
import com.minupay.common.exception.MinuPayException;
import com.minupay.common.idempotency.IdempotencyService;
import com.minupay.payment.application.dto.PaymentCommand;
import com.minupay.payment.application.dto.PaymentInfo;
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

    private static final String PG_NO_RESPONSE_REASON = "PG response timeout";

    private final PaymentService paymentService;
    private final PgClient pgClient;
    private final PgPaymentLogService pgPaymentLogService;
    private final IdempotencyService idempotencyService;
    private final PaymentMetrics paymentMetrics;

    public PaymentInfo request(PaymentCommand command) {
        Optional<PaymentInfo> cached = idempotencyService.findCachedResponse(command.idempotencyKey(), PaymentInfo.class);
        if (cached.isPresent()) return cached.get();

        PaymentService.PaymentInitResult init = paymentService.initiate(command);
        PgResult pgResult = approveWithLog(command, init.paymentId());
        PaymentInfo result = finalizeApproval(command, init, pgResult);

        idempotencyService.complete(command.idempotencyKey(), result);
        return result;
    }

    public PaymentInfo cancel(String paymentId, String reason, String idempotencyKey) {
        Optional<PaymentInfo> cached = idempotencyService.findCachedResponse(idempotencyKey, PaymentInfo.class);
        if (cached.isPresent()) return cached.get();

        PaymentService.CancelReservation reservation = paymentService.reserveCancel(paymentId, idempotencyKey);
        PgResult pgResult = cancelWithLog(paymentId, reservation.pgTxId(), reason);
        if (!isSuccess(pgResult)) {
            throw new MinuPayException(ErrorCode.PG_APPROVAL_FAILED, "PG cancel failed");
        }

        PaymentInfo cancelled = paymentService.confirmCancel(paymentId, reservation.userId(), reservation.amount(), pgResult);
        paymentMetrics.recordCancelled();
        idempotencyService.complete(idempotencyKey, cancelled);
        return cancelled;
    }

    private PgResult approveWithLog(PaymentCommand command, String paymentId) {
        long startTime = System.nanoTime();
        PgResult pgResult = null;
        try {
            pgResult = pgClient.approve(new PgApproveRequest(command.tossPaymentKey(), paymentId, command.amount()));
            return pgResult;
        } finally {
            long durationNs = System.nanoTime() - startTime;
            paymentMetrics.recordPgApproveDuration(durationNs);
            pgPaymentLogService.save(paymentId, PgProvider.TOSS.name(), "APPROVE",
                    Map.of("paymentKey", command.tossPaymentKey(), "amount", command.amount()),
                    pgResponseLog(pgResult), TimeUnit.NANOSECONDS.toMillis(durationNs));
        }
    }

    private PgResult cancelWithLog(String paymentId, String pgTxId, String reason) {
        long startTime = System.currentTimeMillis();
        PgResult pgResult = null;
        try {
            pgResult = pgClient.cancel(pgTxId, reason);
            return pgResult;
        } finally {
            long duration = System.currentTimeMillis() - startTime;
            pgPaymentLogService.save(paymentId, PgProvider.TOSS.name(), "CANCEL",
                    Map.of("pgTxId", pgTxId, "reason", reason),
                    pgResponseLog(pgResult), duration);
        }
    }

    private PaymentInfo finalizeApproval(PaymentCommand command, PaymentService.PaymentInitResult init, PgResult pgResult) {
        if (isSuccess(pgResult)) {
            paymentMetrics.recordApproved();
            return paymentService.approve(init.paymentId(), init.walletTransactionId(), pgResult);
        }
        paymentMetrics.recordFailed();
        String reason = pgResult != null ? pgResult.errorMessage() : PG_NO_RESPONSE_REASON;
        return paymentService.fail(init.paymentId(), init.walletTransactionId(), command.userId(), command.amount(), reason);
    }

    private static boolean isSuccess(PgResult pgResult) {
        return pgResult != null && pgResult.success();
    }

    private static Map<String, Object> pgResponseLog(PgResult pgResult) {
        if (pgResult == null) return Map.of("error", "no_response");
        return Map.of("success", pgResult.success());
    }
}
