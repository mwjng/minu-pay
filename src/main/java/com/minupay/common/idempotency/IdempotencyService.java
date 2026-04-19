package com.minupay.common.idempotency;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.minupay.common.exception.ErrorCode;
import com.minupay.common.exception.MinuPayException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class IdempotencyService {

    private final IdempotencyKeyRepository repository;
    private final ObjectMapper objectMapper;

    /**
     * Returns the cached response if this key already completed. Caller should short-circuit on a hit.
     */
    @Transactional(readOnly = true)
    public <T> Optional<T> findCachedResponse(String key, Class<T> responseType) {
        return repository.findByKeyValue(key)
                .filter(entity -> entity.getStatus() == IdempotencyStatus.COMPLETED && entity.getResponseBody() != null)
                .map(entity -> deserialize(entity.getResponseBody(), responseType));
    }

    /**
     * Reserves the key in PROCESSING state. Caller must run this inside the business transaction so the
     * preemption and downstream writes commit together. Duplicate keys surface as unique-constraint violations.
     */
    public void markProcessing(String key) {
        repository.save(IdempotencyKeyEntity.processing(key));
    }

    /**
     * Serializes the response and attaches it to the key. Serialization failures are logged, not thrown — the
     * business result has already been produced; a missing cached body only costs a replay on retry.
     */
    @Transactional
    public void complete(String key, Object response) {
        repository.findByKeyValue(key).ifPresent(entity -> {
            try {
                entity.complete(objectMapper.writeValueAsString(response));
                repository.save(entity);
            } catch (JsonProcessingException e) {
                log.error("Failed to serialize idempotency response key={}", key, e);
            }
        });
    }

    private <T> T deserialize(String json, Class<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException e) {
            throw new MinuPayException(ErrorCode.INTERNAL_ERROR);
        }
    }
}
