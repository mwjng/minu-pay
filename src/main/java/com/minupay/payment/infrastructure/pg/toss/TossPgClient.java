package com.minupay.payment.infrastructure.pg.toss;

import com.minupay.payment.infrastructure.pg.PgApproveRequest;
import com.minupay.payment.infrastructure.pg.PgClient;
import com.minupay.payment.infrastructure.pg.PgResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

@Slf4j
@Component
public class TossPgClient implements PgClient {

    private final RestClient restClient;

    public TossPgClient(
            @Value("${toss.base-url}") String baseUrl,
            @Value("${toss.secret-key}") String secretKey
    ) {
        String encoded = Base64.getEncoder().encodeToString((secretKey + ":").getBytes(StandardCharsets.UTF_8));
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("Authorization", "Basic " + encoded)
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    @Override
    @SuppressWarnings("unchecked")
    public PgResult approve(PgApproveRequest request) {
        try {
            Map<String, Object> body = Map.of(
                    "paymentKey", request.paymentKey(),
                    "orderId", request.orderId(),
                    "amount", request.amount()
            );
            Map<String, Object> response = restClient.post()
                    .uri("/payments/confirm")
                    .body(body)
                    .retrieve()
                    .body(Map.class);

            String pgTxId = response != null ? (String) response.get("paymentKey") : null;
            return PgResult.success(pgTxId, response);
        } catch (Exception e) {
            log.error("Toss approve failed: {}", e.getMessage());
            return PgResult.failure(e.getMessage(), null);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public PgResult cancel(String pgTxId, String reason) {
        try {
            Map<String, Object> body = Map.of("cancelReason", reason);
            Map<String, Object> response = restClient.post()
                    .uri("/payments/{paymentKey}/cancel", pgTxId)
                    .body(body)
                    .retrieve()
                    .body(Map.class);

            return PgResult.success(pgTxId, response);
        } catch (Exception e) {
            log.error("Toss cancel failed: {}", e.getMessage());
            return PgResult.failure(e.getMessage(), null);
        }
    }
}
