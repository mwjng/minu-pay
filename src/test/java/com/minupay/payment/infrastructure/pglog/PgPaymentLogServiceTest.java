package com.minupay.payment.infrastructure.pglog;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@DisplayName("PgPaymentLogService — Mongo 장애가 결제 트랜잭션에 새지 않는다")
class PgPaymentLogServiceTest {

    @Test
    @DisplayName("Mongo 저장 실패 시 예외를 삼켜서 호출자에게 전파되지 않는다")
    void mongoFailure_swallowed() {
        PgPaymentLogRepository repository = mock(PgPaymentLogRepository.class);
        given(repository.save(any()))
                .willThrow(new DataAccessResourceFailureException("mongo down"));
        PgPaymentLogService service = new PgPaymentLogService(repository);

        assertThatCode(() -> service.save("payment-1", "TOSS", "APPROVE",
                Map.of("k", "v"), Map.of("success", true), 42L))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("정상 저장 시 repository 가 호출된다")
    void success_invokesRepository() {
        PgPaymentLogRepository repository = mock(PgPaymentLogRepository.class);
        PgPaymentLogService service = new PgPaymentLogService(repository);

        service.save("payment-1", "TOSS", "APPROVE",
                Map.of("k", "v"), Map.of("success", true), 42L);

        verify(repository).save(any());
    }

    @Test
    @DisplayName("Mongo 예외가 호출자로 전파되지 않는다")
    void mongoFailure_doesNotPropagate() {
        PgPaymentLogRepository repository = mock(PgPaymentLogRepository.class);
        given(repository.save(any()))
                .willThrow(new RuntimeException("boom"));
        PgPaymentLogService service = new PgPaymentLogService(repository);

        assertThatCode(() -> service.save("p", "TOSS", "APPROVE",
                Map.of(), Map.of(), 1L))
                .doesNotThrowAnyException();
    }
}
