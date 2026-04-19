package com.minupay.common.outbox;

import com.minupay.common.exception.ErrorCode;
import com.minupay.common.exception.MinuPayException;
import com.minupay.common.money.Money;
import com.minupay.wallet.application.WalletService;
import com.minupay.wallet.application.dto.ChargeCommand;
import com.minupay.wallet.domain.WalletRepository;
import com.minupay.wallet.domain.WalletTransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

@SpringBootTest
@ActiveProfiles("local")
@DisplayName("Outbox 트랜잭션 보장 — 롤백 시 이벤트가 발행되지 않는다")
class OutboxRollbackTest {

    @Autowired private WalletService walletService;
    @Autowired private OutboxRepository outboxRepository;
    @Autowired private TransactionTemplate transactionTemplate;

    @MockitoBean
    private WalletTransactionRepository walletTransactionRepository;

    private static final Long USER_ID = 2001L;

    @BeforeEach
    void setUp() {
        walletService.createWallet(USER_ID);
    }

    @Test
    @DisplayName("charge 트랜잭션이 롤백되면 Outbox 에도 이벤트가 남지 않는다")
    void rollback_removes_outbox_row() {
        int before = outboxRepository.findTop50ByStatusOrderByCreatedAtAsc(OutboxStatus.PENDING).size();

        // WalletTransaction save 가 터지면 @Transactional 전체가 롤백됨.
        given(walletTransactionRepository.save(any()))
                .willThrow(new MinuPayException(ErrorCode.INTERNAL_ERROR, "simulated"));

        assertThatThrownBy(() -> walletService.charge(new ChargeCommand(
                USER_ID, Money.of(500L), "ref", "CHARGE_REQUEST",
                "rollback-" + UUID.randomUUID())))
                .isInstanceOf(MinuPayException.class);

        int after = outboxRepository.findTop50ByStatusOrderByCreatedAtAsc(OutboxStatus.PENDING).size();
        assertThat(after).isEqualTo(before);
    }
}
