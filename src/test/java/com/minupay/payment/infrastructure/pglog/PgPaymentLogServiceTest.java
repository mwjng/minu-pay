package com.minupay.payment.infrastructure.pglog;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

@DisplayName("PgPaymentLogService — Mongo 장애가 결제 트랜잭션에 새지 않는다")
class PgPaymentLogServiceTest {

    @Test
    @DisplayName("Mongo 저장 실패 시 예외를 삼키고 null 을 반환한다")
    void mongoFailure_swallowed() {
        PgPaymentLogRepository repository = mock(PgPaymentLogRepository.class);
        given(repository.save(any()))
                .willThrow(new DataAccessResourceFailureException("mongo down"));
        PgPaymentLogService service = new PgPaymentLogService(repository);

        String id = service.save("payment-1", "TOSS", "APPROVE",
                Map.of("k", "v"), Map.of("success", true), 42L);

        assertThat(id).isNull();
    }

    @Test
    @DisplayName("정상 저장 시 생성된 id 를 반환한다")
    void success_returnsId() {
        PgPaymentLogRepository repository = mock(PgPaymentLogRepository.class);
        PgPaymentLog stored = PgPaymentLog.of(
                "payment-1", "TOSS", "APPROVE", Map.of("k", "v"), Map.of("success", true), 42L);
        ReflectionTestUtils.setField(stored, "id", "mongo-id-1");
        given(repository.save(any())).willReturn(stored);
        PgPaymentLogService service = new PgPaymentLogService(repository);

        String id = service.save("payment-1", "TOSS", "APPROVE",
                Map.of("k", "v"), Map.of("success", true), 42L);

        assertThat(id).isEqualTo("mongo-id-1");
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
