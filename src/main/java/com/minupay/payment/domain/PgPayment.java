package com.minupay.payment.domain;

public class PgPayment {

    private Long id;
    private final PgProvider pgProvider;
    private final String pgTxId;
    private final PgStatus status;
    private final String pgLogId;

    private PgPayment(PgProvider pgProvider, String pgTxId, PgStatus status, String pgLogId) {
        this.pgProvider = pgProvider;
        this.pgTxId = pgTxId;
        this.status = status;
        this.pgLogId = pgLogId;
    }

    public static PgPayment approved(PgProvider provider, String pgTxId, String pgLogId) {
        return new PgPayment(provider, pgTxId, PgStatus.APPROVED, pgLogId);
    }

    public static PgPayment cancelled(PgProvider provider, String pgTxId, String pgLogId) {
        return new PgPayment(provider, pgTxId, PgStatus.CANCELLED, pgLogId);
    }

    public static PgPayment of(Long id, PgProvider provider, String pgTxId, PgStatus status, String pgLogId) {
        PgPayment p = new PgPayment(provider, pgTxId, status, pgLogId);
        p.id = id;
        return p;
    }

    public Long getId() {
        return id;
    }

    public PgProvider getPgProvider() {
        return pgProvider;
    }

    public String getPgTxId() {
        return pgTxId;
    }

    public PgStatus getStatus() {
        return status;
    }

    public String getPgLogId() {
        return pgLogId;
    }
}
