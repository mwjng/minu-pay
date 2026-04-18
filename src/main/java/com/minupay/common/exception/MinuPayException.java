package com.minupay.common.exception;

public class MinuPayException extends RuntimeException {

    private final ErrorCode errorCode;

    public MinuPayException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    public MinuPayException(ErrorCode errorCode, String detail) {
        super(detail);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
