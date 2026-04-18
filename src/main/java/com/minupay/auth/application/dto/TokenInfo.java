package com.minupay.auth.application.dto;

public record TokenInfo(String accessToken, String tokenType) {

    public static TokenInfo of(String accessToken) {
        return new TokenInfo(accessToken, "Bearer");
    }
}
