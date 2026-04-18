package com.minupay.payment.infrastructure.pglog;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.Map;

@Document(collection = "pg_payment_logs")
public class PgPaymentLog {

    @Id
    private String id;

    @Indexed
    private String paymentId;

    private String pgProvider;
    private String requestType;
    private Map<String, Object> request;
    private Map<String, Object> response;
    private long durationMs;
    private String traceId;
    private Instant createdAt;

    private PgPaymentLog() {}

    public static PgPaymentLog of(
            String paymentId, String pgProvider, String requestType,
            Map<String, Object> request, Map<String, Object> response,
            long durationMs
    ) {
        PgPaymentLog log = new PgPaymentLog();
        log.paymentId = paymentId;
        log.pgProvider = pgProvider;
        log.requestType = requestType;
        log.request = request;
        log.response = response;
        log.durationMs = durationMs;
        log.createdAt = Instant.now();
        return log;
    }

    public String getId() { return id; }
    public String getPaymentId() { return paymentId; }
}
