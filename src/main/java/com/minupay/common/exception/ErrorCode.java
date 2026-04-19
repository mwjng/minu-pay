package com.minupay.common.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {

    // Common
    INVALID_INPUT(HttpStatus.BAD_REQUEST, "C001", "Invalid input value"),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "C002", "Internal server error"),
    DUPLICATE_REQUEST(HttpStatus.CONFLICT, "C003", "Duplicate request"),

    // Wallet
    WALLET_NOT_FOUND(HttpStatus.NOT_FOUND, "W001", "Wallet not found"),
    INSUFFICIENT_BALANCE(HttpStatus.UNPROCESSABLE_ENTITY, "W002", "Insufficient balance"),
    WALLET_NOT_ACTIVE(HttpStatus.UNPROCESSABLE_ENTITY, "W003", "Wallet is not active"),

    // Payment
    PAYMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "P001", "Payment not found"),
    INVALID_PAYMENT_STATUS(HttpStatus.UNPROCESSABLE_ENTITY, "P002", "Invalid payment status transition"),
    PG_APPROVAL_FAILED(HttpStatus.BAD_GATEWAY, "P003", "PG approval failed"),

    // Auth
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "A001", "Unauthorized"),
    FORBIDDEN(HttpStatus.FORBIDDEN, "A002", "Forbidden"),
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "A003", "Invalid or expired token"),

    // Audit
    AUDIT_LOG_NOT_FOUND(HttpStatus.NOT_FOUND, "AU001", "Audit log not found");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;

    ErrorCode(HttpStatus httpStatus, String code, String message) {
        this.httpStatus = httpStatus;
        this.code = code;
        this.message = message;
    }

    public HttpStatus getHttpStatus() { return httpStatus; }
    public String getCode() { return code; }
    public String getMessage() { return message; }
}
