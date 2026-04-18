package com.minupay.payment.infrastructure.pg;

public record PgResult(
        boolean success,
        String pgTxId,
        String errorMessage,
        Object rawResponse
) {
    public static PgResult success(String pgTxId, Object rawResponse) {
        return new PgResult(true, pgTxId, null, rawResponse);
    }

    public static PgResult failure(String errorMessage, Object rawResponse) {
        return new PgResult(false, null, errorMessage, rawResponse);
    }
}
