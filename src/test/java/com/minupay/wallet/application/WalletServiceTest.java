package com.minupay.wallet.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.minupay.common.exception.MinuPayException;
import com.minupay.common.money.Money;
import com.minupay.common.outbox.Outbox;
import com.minupay.common.outbox.OutboxRepository;
import com.minupay.wallet.application.dto.ChargeCommand;
import com.minupay.wallet.application.dto.WalletInfo;
import com.minupay.wallet.domain.Wallet;
import com.minupay.wallet.domain.WalletRepository;
import com.minupay.wallet.domain.WalletStatus;
import com.minupay.wallet.domain.WalletTransactionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("WalletService 테스트")
class WalletServiceTest {

    @InjectMocks
    private WalletService walletService;

    @Mock private WalletRepository walletRepository;
    @Mock private WalletTransactionRepository walletTransactionRepository;
    @Mock private OutboxRepository outboxRepository;
    @Mock private ObjectMapper objectMapper;

    @Test
    @DisplayName("지갑 생성 성공")
    void 지갑생성_성공() {
        Long userId = 1L;
        Wallet saved = Wallet.of(10L, userId, Money.ZERO, WalletStatus.ACTIVE, 0L);
        given(walletRepository.findByUserId(userId)).willReturn(Optional.empty());
        given(walletRepository.save(any())).willReturn(saved);

        WalletInfo result = walletService.createWallet(userId);

        assertThat(result.userId()).isEqualTo(userId);
        assertThat(result.balance()).isZero();
    }

    @Test
    @DisplayName("이미 지갑이 있는 경우 생성 시 예외 발생")
    void 지갑생성_중복시_예외발생() {
        Long userId = 1L;
        given(walletRepository.findByUserId(userId))
                .willReturn(Optional.of(Wallet.of(10L, userId, Money.ZERO, WalletStatus.ACTIVE, 0L)));

        assertThatThrownBy(() -> walletService.createWallet(userId))
                .isInstanceOf(MinuPayException.class);
    }

    @Test
    @DisplayName("충전 성공 시 Outbox에 WalletCharged 이벤트가 저장된다")
    void 충전_성공_Outbox_이벤트저장() throws Exception {
        Long userId = 1L;
        Wallet wallet = Wallet.of(10L, userId, Money.of(1000L), WalletStatus.ACTIVE, 0L);
        ChargeCommand command = new ChargeCommand(userId, Money.of(500L), "ref-1", "CHARGE_REQUEST");

        given(walletRepository.findByUserIdWithLock(userId)).willReturn(Optional.of(wallet));
        given(walletRepository.save(any())).willReturn(wallet);
        given(walletTransactionRepository.save(any())).willReturn(null);
        given(objectMapper.writeValueAsString(any())).willReturn("{}");

        walletService.charge(command);

        then(outboxRepository).should().save(any(Outbox.class));
    }

    @Test
    @DisplayName("지갑이 없는 경우 차감 시 예외 발생")
    void 차감_지갑없을시_예외발생() {
        Long userId = 1L;
        given(walletRepository.findByUserIdWithLock(userId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> walletService.deduct(
                new ChargeCommand(userId, Money.of(100L), "payment-1", "PAYMENT")))
                .isInstanceOf(MinuPayException.class);
    }
}
