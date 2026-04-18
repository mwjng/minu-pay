package com.minupay.wallet.domain;

import com.minupay.common.exception.MinuPayException;
import com.minupay.common.money.Money;
import com.minupay.wallet.domain.event.WalletCharged;
import com.minupay.wallet.domain.event.WalletDeducted;
import com.minupay.wallet.domain.event.WalletRefunded;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

@DisplayName("Wallet 도메인 테스트")
class WalletTest {

    private Wallet wallet;

    @BeforeEach
    void setUp() {
        wallet = Wallet.of(1L, 100L, Money.of(1000L), WalletStatus.ACTIVE, 0L);
    }

    @Test
    @DisplayName("충전 성공 시 잔액이 증가하고 WalletCharged 이벤트가 발행된다")
    void 충전_성공_잔액증가_이벤트발행() {
        WalletTransaction tx = wallet.charge(Money.of(500L), "ref-1", "CHARGE_REQUEST");

        assertThat(wallet.getBalance()).isEqualTo(Money.of(1500L));
        assertThat(tx.getType()).isEqualTo(WalletTransactionType.CHARGE);
        assertThat(wallet.getDomainEvents()).hasSize(1);
        assertThat(wallet.getDomainEvents().get(0)).isInstanceOf(WalletCharged.class);
    }

    @Test
    @DisplayName("차감 성공 시 잔액이 감소하고 WalletDeducted 이벤트가 발행된다")
    void 차감_성공_잔액감소_이벤트발행() {
        WalletTransaction tx = wallet.deduct(Money.of(300L), "payment-1", "PAYMENT");

        assertThat(wallet.getBalance()).isEqualTo(Money.of(700L));
        assertThat(tx.getType()).isEqualTo(WalletTransactionType.DEDUCT);
        assertThat(wallet.getDomainEvents()).hasSize(1);
        assertThat(wallet.getDomainEvents().get(0)).isInstanceOf(WalletDeducted.class);
    }

    @Test
    @DisplayName("잔액 부족 시 차감하면 예외가 발생한다")
    void 잔액_부족시_차감_예외발생() {
        assertThatThrownBy(() -> wallet.deduct(Money.of(2000L), "payment-1", "PAYMENT"))
                .isInstanceOf(MinuPayException.class)
                .hasMessageContaining("Insufficient");
    }

    @Test
    @DisplayName("환불 성공 시 잔액이 증가하고 WalletRefunded 이벤트가 발행된다")
    void 환불_성공_잔액증가_이벤트발행() {
        WalletTransaction tx = wallet.refund(Money.of(200L), "payment-1", "PAYMENT");

        assertThat(wallet.getBalance()).isEqualTo(Money.of(1200L));
        assertThat(tx.getType()).isEqualTo(WalletTransactionType.REFUND);
        assertThat(wallet.getDomainEvents().get(0)).isInstanceOf(WalletRefunded.class);
    }

    @Test
    @DisplayName("FROZEN 상태에서 차감하면 예외가 발생한다")
    void FROZEN_상태_차감_예외발생() {
        wallet.freeze();

        assertThatThrownBy(() -> wallet.deduct(Money.of(100L), "payment-1", "PAYMENT"))
                .isInstanceOf(MinuPayException.class);
    }

    @Test
    @DisplayName("FROZEN 상태에서 충전하면 예외가 발생한다")
    void FROZEN_상태_충전_예외발생() {
        wallet.freeze();

        assertThatThrownBy(() -> wallet.charge(Money.of(100L), "ref-1", "CHARGE_REQUEST"))
                .isInstanceOf(MinuPayException.class);
    }

    @Test
    @DisplayName("잔액과 정확히 같은 금액은 차감이 성공한다")
    void 잔액_정확히_같은금액_차감_성공() {
        assertThatCode(() -> wallet.deduct(Money.of(1000L), "payment-1", "PAYMENT"))
                .doesNotThrowAnyException();
        assertThat(wallet.getBalance()).isEqualTo(Money.ZERO);
    }

    @Test
    @DisplayName("이벤트 클리어 후 도메인 이벤트 목록이 비어있다")
    void 이벤트_클리어후_빈리스트() {
        wallet.charge(Money.of(100L), "ref-1", "CHARGE_REQUEST");
        wallet.clearDomainEvents();

        assertThat(wallet.getDomainEvents()).isEmpty();
    }

    @Test
    @DisplayName("여러 번 충전과 차감을 반복해도 잔액 정합성이 유지된다")
    void 반복_충전_차감_잔액정합성() {
        wallet.charge(Money.of(500L), "ref-1", "CHARGE_REQUEST");  // 1500
        wallet.deduct(Money.of(200L), "payment-1", "PAYMENT");      // 1300
        wallet.charge(Money.of(100L), "ref-2", "CHARGE_REQUEST");  // 1400
        wallet.deduct(Money.of(400L), "payment-2", "PAYMENT");      // 1000

        assertThat(wallet.getBalance()).isEqualTo(Money.of(1000L));
        assertThat(wallet.getDomainEvents()).hasSize(4);
    }
}
