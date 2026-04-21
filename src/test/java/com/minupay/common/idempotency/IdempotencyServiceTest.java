package com.minupay.common.idempotency;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

class IdempotencyServiceTest {

    private IdempotencyKeyRepository repository;
    private IdempotencyService service;

    record SampleResponse(String message, int value) {}

    @BeforeEach
    void setUp() {
        repository = mock(IdempotencyKeyRepository.class);
        service = new IdempotencyService(repository, new ObjectMapper());
    }

    @Test
    @DisplayName("완료된_멱등키가_있으면_캐시된_응답을_역직렬화해서_반환한다")
    void findCachedResponse_returnsDeserialized() {
        IdempotencyKeyEntity completed = IdempotencyKeyEntity.processing("key-1");
        completed.complete("{\"message\":\"ok\",\"value\":42}");
        given(repository.findByKeyValue("key-1")).willReturn(Optional.of(completed));

        Optional<SampleResponse> cached = service.findCachedResponse("key-1", SampleResponse.class);

        assertThat(cached).isPresent();
        assertThat(cached.get().message()).isEqualTo("ok");
        assertThat(cached.get().value()).isEqualTo(42);
    }

    @Test
    @DisplayName("PROCESSING_상태의_멱등키는_캐시_히트로_간주하지_않는다")
    void findCachedResponse_processingIsNotHit() {
        given(repository.findByKeyValue("key-1"))
                .willReturn(Optional.of(IdempotencyKeyEntity.processing("key-1")));

        Optional<SampleResponse> cached = service.findCachedResponse("key-1", SampleResponse.class);

        assertThat(cached).isEmpty();
    }

    @Test
    @DisplayName("markProcessing은_PROCESSING_상태의_엔티티를_저장한다")
    void markProcessing_savesProcessingEntity() {
        service.markProcessing("key-1");

        verify(repository).save(argThat(e ->
                e.getKeyValue().equals("key-1") && e.getStatus() == IdempotencyStatus.PROCESSING
        ));
    }

    @Test
    @DisplayName("complete는_응답을_직렬화해서_저장하고_COMPLETED로_전환한다")
    void complete_savesSerializedResponse() {
        IdempotencyKeyEntity processing = IdempotencyKeyEntity.processing("key-1");
        given(repository.findByKeyValue("key-1")).willReturn(Optional.of(processing));

        service.complete("key-1", new SampleResponse("done", 7));

        assertThat(processing.getStatus()).isEqualTo(IdempotencyStatus.COMPLETED);
        assertThat(processing.getResponseBody()).contains("\"message\":\"done\"").contains("\"value\":7");
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("complete에_해당_키가_없으면_아무것도_하지_않는다")
    void complete_missingKey_noop() {
        given(repository.findByKeyValue("key-1")).willReturn(Optional.empty());

        service.complete("key-1", new SampleResponse("x", 0));

        verify(repository, never()).save(any());
    }
}
