package com.minupay.payment.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.minupay.common.event.EventEnvelope;
import com.minupay.common.exception.ErrorCode;
import com.minupay.common.exception.MinuPayException;
import com.minupay.common.idempotency.IdempotencyService;
import com.minupay.common.money.Money;
import com.minupay.common.outbox.Outbox;
import com.minupay.common.outbox.OutboxRepository;
import com.minupay.payment.application.dto.PaymentCommand;
import com.minupay.payment.application.dto.PaymentInfo;
import com.minupay.payment.domain.Payment;
import com.minupay.payment.domain.PgPayment;
import com.minupay.payment.domain.PgProvider;
import com.minupay.payment.domain.PaymentRepository;
import com.minupay.payment.infrastructure.pg.PgResult;
import com.minupay.wallet.application.WalletService;
import com.minupay.wallet.application.dto.ChargeCommand;
import com.minupay.wallet.application.dto.WalletDeductResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final WalletService walletService;
    private final IdempotencyService idempotencyService;
    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public PaymentInitResult initiate(PaymentCommand command) {
        idempotencyService.markProcessing(command.idempotencyKey());

        Payment payment = Payment.create(command.userId(), command.merchantId(), Money.of(command.amount()), command.idempotencyKey());
        paymentRepository.save(payment);

        WalletDeductResult deductResult = walletService.deductForPayment(
                new ChargeCommand(command.userId(), Money.of(command.amount()), payment.getId(), "PAYMENT", null)
        );

        return new PaymentInitResult(payment.getId(), deductResult.transactionId());
    }

    @Transactional
    public PaymentInfo approve(String paymentId, Long walletTransactionId, PgResult pgResult) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new MinuPayException(ErrorCode.PAYMENT_NOT_FOUND));

        PgPayment pgPayment = PgPayment.approved(PgProvider.TOSS, pgResult.pgTxId(), null);
        payment.approve(walletTransactionId, pgPayment);

        Payment saved = paymentRepository.save(payment);
        publishEvents(payment);
        return PaymentInfo.from(saved);
    }

    @Transactional
    public PaymentInfo fail(String paymentId, Long walletTransactionId, Long userId, long amount, String reason) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new MinuPayException(ErrorCode.PAYMENT_NOT_FOUND));

        payment.fail(reason);
        paymentRepository.save(payment);

        walletService.refund(new ChargeCommand(userId, Money.of(amount), paymentId, "PAYMENT_FAIL_REFUND", null));
        publishEvents(payment);
        return PaymentInfo.from(payment);
    }

    @Transactional
    public CancelReservation reserveCancel(String paymentId, String idempotencyKey) {
        Payment payment = paymentRepository.findByIdWithLock(paymentId)
                .orElseThrow(() -> new MinuPayException(ErrorCode.PAYMENT_NOT_FOUND));
        if (payment.getStatus() != com.minupay.payment.domain.PaymentStatus.APPROVED) {
            throw new MinuPayException(ErrorCode.INVALID_PAYMENT_STATUS, "Only APPROVED payment can be cancelled");
        }
        idempotencyService.markProcessing(idempotencyKey);
        return new CancelReservation(payment.getUserId(), payment.getAmount(), payment.getPgPayment().getPgTxId());
    }

    @Transactional
    public PaymentInfo confirmCancel(String paymentId, Long userId, Money amount, PgResult pgResult) {
        Payment payment = paymentRepository.findByIdWithLock(paymentId)
                .orElseThrow(() -> new MinuPayException(ErrorCode.PAYMENT_NOT_FOUND));

        PgPayment cancelPgPayment = PgPayment.cancelled(PgProvider.TOSS, pgResult.pgTxId(), null);
        payment.cancel(cancelPgPayment);
        paymentRepository.save(payment);

        walletService.refund(new ChargeCommand(userId, amount, paymentId, "PAYMENT_CANCEL_REFUND", null));
        publishEvents(payment);
        return PaymentInfo.from(payment);
    }

    private void publishEvents(Payment payment) {
        payment.getDomainEvents().forEach(event -> {
            try {
                String payload = EventEnvelope.from(event).toJson(objectMapper);
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

    record PaymentInitResult(String paymentId, Long walletTransactionId) {}

    public record CancelReservation(Long userId, Money amount, String pgTxId) {}
}
