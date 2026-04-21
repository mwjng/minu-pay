package com.minupay.wallet.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.minupay.common.exception.MinuPayException;
import com.minupay.common.idempotency.IdempotencyService;
import com.minupay.common.money.Money;
import com.minupay.common.outbox.Outbox;
import com.minupay.common.outbox.OutboxRepository;
import com.minupay.wallet.application.dto.ChargeCommand;
import com.minupay.wallet.application.dto.WalletInfo;
import com.minupay.wallet.domain.Wallet;
import com.minupay.wallet.domain.WalletRepository;
import com.minupay.wallet.domain.WalletStatus;
import com.minupay.wallet.domain.WalletTransactionRepository;
import com.minupay.wallet.infrastructure.metrics.WalletMetrics;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.*;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
@DisplayName("WalletService 테스트")
class WalletServiceTest {

    @InjectMocks
    private WalletService walletService;

    @Mock private WalletRepository walletRepository;
    @Mock private WalletTransactionRepository walletTransactionRepository;
    @Mock private OutboxRepository outboxRepository;
    @Mock private ObjectMapper objectMapper;
    @Mock private IdempotencyService idempotencyService;
    @Mock private WalletMetrics walletMetrics;

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
    @DisplayName("충전 성공 시 Outbox에 WalletCharged 이벤트가 저장되고 멱등키가 완료 처리된다")
    void 충전_성공_Outbox_이벤트저장() throws Exception {
        Long userId = 1L;
        String key = "key-1";
        Wallet wallet = Wallet.of(10L, userId, Money.of(1000L), WalletStatus.ACTIVE, 0L);
        ChargeCommand command = new ChargeCommand(userId, Money.of(500L), "ref-1", "CHARGE_REQUEST", key);

        given(idempotencyService.findCachedResponse(key, WalletInfo.class)).willReturn(Optional.empty());
        given(walletRepository.findByUserIdWithLock(userId)).willReturn(Optional.of(wallet));
        given(walletRepository.save(any())).willReturn(wallet);
        given(walletTransactionRepository.save(any())).willReturn(null);
        given(objectMapper.writeValueAsString(any())).willReturn("{}");

        walletService.charge(command);

        then(idempotencyService).should().markProcessing(key);
        then(outboxRepository).should().save(any(Outbox.class));
        then(idempotencyService).should().complete(eq(key), any(WalletInfo.class));
    }

    @Test
    @DisplayName("동일 idempotencyKey 재요청시 캐시된 응답이 반환되고 잔액은 건드리지 않는다")
    void 충전_중복요청_캐시응답반환() {
        Long userId = 1L;
        String key = "key-dup";
        WalletInfo cached = new WalletInfo(10L, userId, 2000L, WalletStatus.ACTIVE);
        ChargeCommand command = new ChargeCommand(userId, Money.of(500L), "ref-1", "CHARGE_REQUEST", key);

        given(idempotencyService.findCachedResponse(key, WalletInfo.class)).willReturn(Optional.of(cached));

        WalletInfo result = walletService.charge(command);

        assertThat(result).isEqualTo(cached);
        then(walletRepository).should(never()).findByUserIdWithLock(any());
        then(idempotencyService).should(never()).markProcessing(any());
        then(idempotencyService).should(never()).complete(any(), any());
    }

    @Test
    @DisplayName("idempotencyKey 없이 charge 호출시 예외 발생")
    void 충전_키없으면_예외() {
        ChargeCommand command = new ChargeCommand(1L, Money.of(500L), "ref-1", "CHARGE_REQUEST", null);

        assertThatThrownBy(() -> walletService.charge(command))
                .isInstanceOf(MinuPayException.class)
                .hasMessageContaining("idempotencyKey");
    }

    @Test
    @DisplayName("지갑이 없는 경우 차감 시 예외 발생")
    void 차감_지갑없을시_예외발생() {
        Long userId = 1L;
        given(walletRepository.findByUserIdWithLock(userId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> walletService.deduct(
                new ChargeCommand(userId, Money.of(100L), "payment-1", "PAYMENT", null)))
                .isInstanceOf(MinuPayException.class);
    }
}
