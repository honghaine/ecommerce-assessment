package com.flashsale.common.error;

import java.time.Duration;

/**
 * Business error mapped to an RFC 7807 response by {@link GlobalExceptionHandler}.
 */
public class ApiException extends RuntimeException {

    private final ErrorCode errorCode;
    private final Duration retryAfter;

    public ApiException(ErrorCode errorCode) {
        this(errorCode, null);
    }

    public ApiException(ErrorCode errorCode, Duration retryAfter) {
        super(errorCode.message());
        this.errorCode = errorCode;
        this.retryAfter = retryAfter;
    }

    public ErrorCode errorCode() {
        return errorCode;
    }

    public Duration retryAfter() {
        return retryAfter;
    }
}
