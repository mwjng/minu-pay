package com.minupay.wallet.application;

import com.minupay.common.event.DomainEventPublisher;
import com.minupay.common.exception.ErrorCode;
import com.minupay.common.exception.MinuPayException;
import com.minupay.common.idempotency.IdempotencyService;
import com.minupay.wallet.application.dto.ChargeCommand;
import com.minupay.wallet.application.dto.WalletInfo;
import com.minupay.wallet.domain.Wallet;
import com.minupay.wallet.domain.WalletRepository;
import com.minupay.wallet.domain.WalletTransaction;
import com.minupay.wallet.domain.WalletTransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.minupay.wallet.application.dto.WalletDeductResult;
import com.minupay.wallet.infrastructure.metrics.WalletMetrics;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class WalletService {

    private final WalletRepository walletRepository;
    private final WalletTransactionRepository walletTransactionRepository;
    private final DomainEventPublisher domainEventPublisher;
    private final IdempotencyService idempotencyService;
    private final WalletMetrics walletMetrics;

    @Transactional
    public WalletInfo createWallet(Long userId) {
        if (walletRepository.findByUserId(userId).isPresent()) {
            throw new MinuPayException(ErrorCode.DUPLICATE_REQUEST, "Wallet already exists for this user");
        }
        return WalletInfo.from(walletRepository.save(Wallet.create(userId)));
    }

    @Transactional(readOnly = true)
    public WalletInfo getWalletByUserId(Long userId) {
        return walletRepository.findByUserId(userId)
                .map(WalletInfo::from)
                .orElseThrow(() -> new MinuPayException(ErrorCode.WALLET_NOT_FOUND));
    }

    @Transactional
    public WalletInfo charge(ChargeCommand command) {
        if (command.idempotencyKey() == null) {
            throw new MinuPayException(ErrorCode.INVALID_INPUT, "idempotencyKey is required for charge");
        }

        Optional<WalletInfo> cached = idempotencyService.findCachedResponse(command.idempotencyKey(), WalletInfo.class);
        if (cached.isPresent()) {
            return cached.get();
        }

        idempotencyService.markProcessing(command.idempotencyKey());

        Wallet wallet = walletRepository.findByUserIdWithLock(command.userId())
                .orElseThrow(() -> new MinuPayException(ErrorCode.WALLET_NOT_FOUND));

        WalletTransaction tx = wallet.charge(command.amount(), command.referenceId(), command.referenceType());
        walletRepository.save(wallet);
        walletTransactionRepository.save(tx);
        publishEvents(wallet);

        WalletInfo result = WalletInfo.from(wallet);
        idempotencyService.complete(command.idempotencyKey(), result);
        walletMetrics.recordCharge(command.amount().toLong());
        return result;
    }

    @Transactional
    public WalletInfo deduct(ChargeCommand command) {
        Wallet wallet = walletRepository.findByUserIdWithLock(command.userId())
                .orElseThrow(() -> new MinuPayException(ErrorCode.WALLET_NOT_FOUND));

        WalletTransaction tx = wallet.deduct(command.amount(), command.referenceId(), command.referenceType());
        walletRepository.save(wallet);
        walletTransactionRepository.save(tx);
        publishEvents(wallet);

        return WalletInfo.from(wallet);
    }

    @Transactional
    public WalletInfo internalDeduct(ChargeCommand command) {
        requireIdempotencyKey(command.idempotencyKey());

        Optional<WalletInfo> cached = idempotencyService.findCachedResponse(command.idempotencyKey(), WalletInfo.class);
        if (cached.isPresent()) {
            return cached.get();
        }
        idempotencyService.markProcessing(command.idempotencyKey());

        Wallet wallet = walletRepository.findByUserIdWithLock(command.userId())
                .orElseThrow(() -> new MinuPayException(ErrorCode.WALLET_NOT_FOUND));

        WalletTransaction tx = wallet.deduct(command.amount(), command.referenceId(), command.referenceType());
        walletRepository.save(wallet);
        walletTransactionRepository.save(tx);
        publishEvents(wallet);

        WalletInfo result = WalletInfo.from(wallet);
        idempotencyService.complete(command.idempotencyKey(), result);
        return result;
    }

    @Transactional
    public WalletInfo internalCredit(ChargeCommand command) {
        requireIdempotencyKey(command.idempotencyKey());

        Optional<WalletInfo> cached = idempotencyService.findCachedResponse(command.idempotencyKey(), WalletInfo.class);
        if (cached.isPresent()) {
            return cached.get();
        }
        idempotencyService.markProcessing(command.idempotencyKey());

        Wallet wallet = walletRepository.findByUserIdWithLock(command.userId())
                .orElseThrow(() -> new MinuPayException(ErrorCode.WALLET_NOT_FOUND));

        WalletTransaction tx = wallet.refund(command.amount(), command.referenceId(), command.referenceType());
        walletRepository.save(wallet);
        walletTransactionRepository.save(tx);
        publishEvents(wallet);

        WalletInfo result = WalletInfo.from(wallet);
        idempotencyService.complete(command.idempotencyKey(), result);
        return result;
    }

    private void requireIdempotencyKey(String key) {
        if (key == null || key.isBlank()) {
            throw new MinuPayException(ErrorCode.INVALID_INPUT, "idempotencyKey is required");
        }
    }

    @Transactional
    public WalletDeductResult deductForPayment(ChargeCommand command) {
        Wallet wallet = walletRepository.findByUserIdWithLock(command.userId())
                .orElseThrow(() -> new MinuPayException(ErrorCode.WALLET_NOT_FOUND));

        WalletTransaction tx = wallet.deduct(command.amount(), command.referenceId(), command.referenceType());
        walletRepository.save(wallet);
        WalletTransaction saved = walletTransactionRepository.save(tx);
        publishEvents(wallet);

        return new WalletDeductResult(wallet.getId(), wallet.getBalance().toLong(), saved.getId());
    }

    @Transactional
    public WalletInfo refund(ChargeCommand command) {
        Wallet wallet = walletRepository.findByUserIdWithLock(command.userId())
                .orElseThrow(() -> new MinuPayException(ErrorCode.WALLET_NOT_FOUND));

        WalletTransaction tx = wallet.refund(command.amount(), command.referenceId(), command.referenceType());
        walletRepository.save(wallet);
        walletTransactionRepository.save(tx);
        publishEvents(wallet);

        return WalletInfo.from(wallet);
    }

    private void publishEvents(Wallet wallet) {
        domainEventPublisher.publish(wallet.getDomainEvents());
        wallet.clearDomainEvents();
    }
}
