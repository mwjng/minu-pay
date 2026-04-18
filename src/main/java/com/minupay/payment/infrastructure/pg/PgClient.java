package com.minupay.payment.infrastructure.pg;

public interface PgClient {
    PgResult approve(PgApproveRequest request);
    PgResult cancel(String pgTxId, String reason);
}
