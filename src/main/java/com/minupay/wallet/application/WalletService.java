package com.minupay.wallet.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.minupay.common.exception.ErrorCode;
import com.minupay.common.exception.MinuPayException;
import com.minupay.common.outbox.Outbox;
import com.minupay.common.outbox.OutboxRepository;
import com.minupay.wallet.application.dto.ChargeCommand;
import com.minupay.wallet.application.dto.WalletInfo;
import com.minupay.wallet.domain.Wallet;
import com.minupay.wallet.domain.WalletRepository;
import com.minupay.wallet.domain.WalletTransaction;
import com.minupay.wallet.domain.WalletTransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class WalletService {

    private static final Map<String, String> EVENT_TOPICS = Map.of(
            "WalletCharged", "wallet.charged",
            "WalletDeducted", "wallet.deducted",
            "WalletRefunded", "wallet.refunded"
    );

    private final WalletRepository walletRepository;
    private final WalletTransactionRepository walletTransactionRepository;
    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

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
        Wallet wallet = walletRepository.findByUserIdWithLock(command.userId())
                .orElseThrow(() -> new MinuPayException(ErrorCode.WALLET_NOT_FOUND));

        WalletTransaction tx = wallet.charge(command.amount(), command.referenceId(), command.referenceType());
        walletRepository.save(wallet);
        walletTransactionRepository.save(tx);
        publishEvents(wallet);

        return WalletInfo.from(wallet);
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
        wallet.getDomainEvents().forEach(event -> {
            try {
                String payload = objectMapper.writeValueAsString(event.getPayload());
                String topic = EVENT_TOPICS.getOrDefault(event.getEventType(), "wallet.events");
                outboxRepository.save(Outbox.create(
                        event.getAggregateId(),
                        event.getAggregateType(),
                        event.getEventType(),
                        topic,
                        event.getAggregateId(),
                        payload
                ));
            } catch (JsonProcessingException e) {
                throw new MinuPayException(ErrorCode.INTERNAL_ERROR, "Event serialization failed");
            }
        });
        wallet.clearDomainEvents();
    }
}
