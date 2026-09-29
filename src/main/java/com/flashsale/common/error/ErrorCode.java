package com.flashsale.common.error;

import org.springframework.http.HttpStatus;

/**
 * Stable, client-facing error codes. Messages are deliberately generic so that
 * responses never reveal whether an account exists or which check failed.
 */
public enum ErrorCode {

    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "Request is invalid"),
    INVALID_IDENTIFIER(HttpStatus.BAD_REQUEST, "Identifier must be a valid email or phone number in international format"),
    UNSUPPORTED_REGION(HttpStatus.BAD_REQUEST, "Region is not supported"),
    INVALID_OTP(HttpStatus.BAD_REQUEST, "Verification code is invalid or expired"),

    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "Authentication is required"),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "Invalid credentials"),
    INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "Refresh token is invalid or expired"),

    FORBIDDEN(HttpStatus.FORBIDDEN, "Access is denied"),
    ACCOUNT_NOT_VERIFIED(HttpStatus.FORBIDDEN, "Account is not verified"),
    ACCOUNT_LOCKED(HttpStatus.FORBIDDEN, "Account is locked"),

    IDEMPOTENCY_KEY_REQUIRED(HttpStatus.BAD_REQUEST,
            "Header Idempotency-Key is required (8-64 chars: letters, digits, '-')"),

    NOT_FOUND(HttpStatus.NOT_FOUND, "Resource not found"),
    FLASH_SALE_ITEM_NOT_FOUND(HttpStatus.NOT_FOUND, "Flash sale item not found"),

    FLASH_SALE_NOT_ACTIVE(HttpStatus.CONFLICT, "Flash sale slot is not active"),
    SOLD_OUT(HttpStatus.CONFLICT, "Flash sale item is sold out"),
    ALREADY_PURCHASED_TODAY(HttpStatus.CONFLICT, "Only one flash sale product per user per day"),
    IDEMPOTENCY_KEY_REUSED(HttpStatus.CONFLICT, "Idempotency-Key was already used for another request"),

    INSUFFICIENT_BALANCE(HttpStatus.UNPROCESSABLE_CONTENT, "Insufficient balance"),

    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "Method not allowed"),
    TOO_MANY_REQUESTS(HttpStatus.TOO_MANY_REQUESTS, "Too many requests, please retry later"),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected error");

    private final HttpStatus status;
    private final String message;

    ErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }

    public HttpStatus status() {
        return status;
    }

    public String message() {
        return message;
    }
}
