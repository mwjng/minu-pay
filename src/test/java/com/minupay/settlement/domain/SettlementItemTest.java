package com.minupay.settlement.domain;

import com.minupay.common.money.Money;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SettlementItemTest {

    @Test
    @DisplayName("수수료율_3퍼센트_적용시_grossAmount와_fee_netAmount가_일치한다")
    void record_calculatesFeeAndNetAmount() {
        SettlementItem item = SettlementItem.record(
                "payment-1", "merchant-1", LocalDate.of(2026, 4, 19),
                Money.of(10_000), new BigDecimal("0.03")
        );

        assertThat(item.getGrossAmount()).isEqualTo(Money.of(10_000));
        assertThat(item.getFee()).isEqualTo(Money.of(300));
        assertThat(item.getNetAmount()).isEqualTo(Money.of(9_700));
        assertThat(item.getStatus()).isEqualTo(SettlementItemStatus.INCLUDED);
        assertThat(item.getCancelledAt()).isNull();
    }

    @Test
    @DisplayName("수수료_계산시_소수점은_원_단위_절사된다")
    void record_feeRoundingDown() {
        SettlementItem item = SettlementItem.record(
                "payment-2", "merchant-1", LocalDate.of(2026, 4, 19),
                Money.of(333), new BigDecimal("0.03")
        );

        // 333 * 0.03 = 9.99 → 9
        assertThat(item.getFee()).isEqualTo(Money.of(9));
        assertThat(item.getNetAmount()).isEqualTo(Money.of(324));
    }

    @Test
    @DisplayName("cancel_호출시_상태가_CANCELLED로_바뀌고_취소시각이_기록된다")
    void cancel_transitionsStatus() {
        SettlementItem item = SettlementItem.record(
                "payment-3", "merchant-1", LocalDate.of(2026, 4, 19),
                Money.of(1_000), new BigDecimal("0.03")
        );

        item.cancel();

        assertThat(item.getStatus()).isEqualTo(SettlementItemStatus.CANCELLED);
        assertThat(item.getCancelledAt()).isNotNull();
    }

    @Test
    @DisplayName("이미_취소된_항목을_다시_cancel하면_예외가_발생한다")
    void cancel_twice_throws() {
        SettlementItem item = SettlementItem.record(
                "payment-4", "merchant-1", LocalDate.of(2026, 4, 19),
                Money.of(1_000), new BigDecimal("0.03")
        );
        item.cancel();

        assertThatThrownBy(item::cancel)
                .hasMessageContaining("Already cancelled");
    }
}
