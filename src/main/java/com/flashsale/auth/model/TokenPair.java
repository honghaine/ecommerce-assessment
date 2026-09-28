package com.flashsale.auth.model;

public record TokenPair(String accessToken, long accessExpiresInSeconds, String refreshToken,
                        long refreshExpiresInSeconds) {
}
