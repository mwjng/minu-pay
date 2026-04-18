package com.minupay.wallet.concurrency;

import com.minupay.common.exception.MinuPayException;
import com.minupay.common.money.Money;
import com.minupay.wallet.application.WalletService;
import com.minupay.wallet.application.dto.ChargeCommand;
import com.minupay.wallet.application.dto.WalletInfo;
import com.minupay.wallet.domain.Wallet;
import com.minupay.wallet.domain.WalletRepository;
import com.minupay.wallet.domain.WalletStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("local")
@DisplayName("Wallet 동시성 테스트")
class WalletConcurrencyTest {

    @Autowired private WalletService walletService;
    @Autowired private WalletRepository walletRepository;

    private static final Long USER_ID = 999L;

    @BeforeEach
    void setUp() {
        walletRepository.findByUserId(USER_ID).ifPresentOrElse(
                existing -> {},
                () -> walletService.createWallet(USER_ID)
        );
        // 초기 잔액 10,000원 충전
        walletService.charge(new ChargeCommand(USER_ID, Money.of(10_000L), "init", "INIT"));
    }

    @Test
    @DisplayName("동시에 10번 1,000원 충전 시 최종 잔액이 20,000원이다")
    void 동시_충전_10회_잔액정합성() throws InterruptedException {
        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            int idx = i;
            executor.submit(() -> {
                try {
                    walletService.charge(new ChargeCommand(USER_ID, Money.of(1_000L), "ref-" + idx, "CHARGE_REQUEST"));
                } finally {
                    latch.countDown();
                }
            });
        }
        latch.await();
        executor.shutdown();

        WalletInfo result = walletService.getWalletByUserId(USER_ID);
        assertThat(result.balance()).isEqualTo(20_000L);
    }

    @Test
    @DisplayName("동시에 10번 1,000원 차감 시 최종 잔액이 0원이다")
    void 동시_차감_10회_잔액정합성() throws InterruptedException {
        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger failCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            int idx = i;
            executor.submit(() -> {
                try {
                    walletService.deduct(new ChargeCommand(USER_ID, Money.of(1_000L), "payment-" + idx, "PAYMENT"));
                } catch (MinuPayException e) {
                    failCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }
        latch.await();
        executor.shutdown();

        WalletInfo result = walletService.getWalletByUserId(USER_ID);
        assertThat(result.balance()).isEqualTo(0L);
        assertThat(failCount.get()).isEqualTo(0);
    }

    @Test
    @DisplayName("잔액 초과 동시 차감 시 일부는 실패하고 잔액이 음수가 되지 않는다")
    void 동시_잔액초과_차감_음수방지() throws InterruptedException {
        int threadCount = 20; // 잔액 10,000원에 20번 1,000원 차감 시도
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            int idx = i;
            executor.submit(() -> {
                try {
                    walletService.deduct(new ChargeCommand(USER_ID, Money.of(1_000L), "payment-" + idx, "PAYMENT"));
                    successCount.incrementAndGet();
                } catch (MinuPayException e) {
                    failCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }
        latch.await();
        executor.shutdown();

        WalletInfo result = walletService.getWalletByUserId(USER_ID);
        assertThat(result.balance()).isGreaterThanOrEqualTo(0L);
        assertThat(successCount.get()).isEqualTo(10);
        assertThat(failCount.get()).isEqualTo(10);
    }
}
