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
    INVALID_TIME_WINDOW(HttpStatus.BAD_REQUEST, "Slot must start in the future, end after it starts and last at most 24h"),
    INVALID_SALE_PRICE(HttpStatus.BAD_REQUEST, "Sale price must be lower than the product price"),
    SLOT_TIME_NOT_ALIGNED(HttpStatus.BAD_REQUEST, "Slot start time must match the region's flash-sale windows"),
    INVALID_DAYS_OF_WEEK(HttpStatus.BAD_REQUEST, "At least one day of week is required"),
    INVALID_SLOT_LENGTH(HttpStatus.BAD_REQUEST, "Slot length must divide 24h evenly (e.g. 15, 30, 60, 120, 240 minutes)"),

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
    FLASH_SALE_SESSION_NOT_FOUND(HttpStatus.NOT_FOUND, "Flash sale slot not found"),
    PRODUCT_NOT_FOUND(HttpStatus.NOT_FOUND, "Product not found"),
    RULE_NOT_FOUND(HttpStatus.NOT_FOUND, "Flash sale rule not found"),
    FLASH_SALE_CONFIG_NOT_FOUND(HttpStatus.NOT_FOUND, "Flash sale is not configured for this region"),

    FLASH_SALE_NOT_ACTIVE(HttpStatus.CONFLICT, "Flash sale slot is not active"),
    SOLD_OUT(HttpStatus.CONFLICT, "Flash sale item is sold out"),
    ALREADY_PURCHASED_TODAY(HttpStatus.CONFLICT, "Only one flash sale product per user per day"),
    IDEMPOTENCY_KEY_REUSED(HttpStatus.CONFLICT, "Idempotency-Key was already used for another request"),
    SLOT_ALREADY_EXISTS(HttpStatus.CONFLICT, "A slot with this start time already exists in the region"),
    SLOT_ALREADY_STARTED(HttpStatus.CONFLICT, "Slot has already started; its items can no longer change"),
    ITEM_ALREADY_NOMINATED(HttpStatus.CONFLICT, "Product is already in this slot"),
    RULE_ALREADY_EXISTS(HttpStatus.CONFLICT, "This product already has a rule at this start time"),
    RULE_ARCHIVED(HttpStatus.CONFLICT, "Rule is archived"),
    PRODUCT_INACTIVE(HttpStatus.CONFLICT, "Product is not active"),
    ITEM_NOT_WITHDRAWABLE(HttpStatus.CONFLICT, "Item is not active"),
    SKU_ALREADY_EXISTS(HttpStatus.CONFLICT, "SKU already exists in the region"),
    INSUFFICIENT_STOCK(HttpStatus.CONFLICT, "Not enough available stock for this quota"),

    INSUFFICIENT_BALANCE(HttpStatus.UNPROCESSABLE_CONTENT, "Insufficient balance"),

    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "Method not allowed"),
    TOO_MANY_REQUESTS(HttpStatus.TOO_MANY_REQUESTS, "Too many requests, please retry later"),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected error"),
    SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "Service temporarily unavailable, please retry");

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
