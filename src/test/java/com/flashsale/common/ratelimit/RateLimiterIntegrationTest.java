package com.flashsale.common.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.flashsale.IntegrationTest;
import com.flashsale.common.error.ApiException;
import com.flashsale.common.error.ErrorCode;

class RateLimiterIntegrationTest extends IntegrationTest {

    @Autowired
    private RateLimiter rateLimiter;

    @Test
    void blocksAfterLimitWithinWindow() {
        String key = UUID.randomUUID().toString();

        for (int i = 0; i < 3; i++) {
            rateLimiter.check("test-scope", key);
        }

        assertThatThrownBy(() -> rateLimiter.check("test-scope", key))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.errorCode()).isEqualTo(ErrorCode.TOO_MANY_REQUESTS);
                    assertThat(ex.retryAfter()).isPositive();
                });
    }

    @Test
    void keysAreIndependent() {
        String a = UUID.randomUUID().toString();
        String b = UUID.randomUUID().toString();
        for (int i = 0; i < 3; i++) {
            rateLimiter.check("test-scope", a);
        }

        rateLimiter.check("test-scope", b);
    }
}
