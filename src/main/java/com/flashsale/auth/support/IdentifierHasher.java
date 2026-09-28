package com.flashsale.auth.support;

import org.springframework.stereotype.Component;

import com.flashsale.auth.config.AuthProperties;
import com.flashsale.auth.model.Identifier;

/**
 * Keyed hash of an identifier, used wherever an identifier becomes a Redis key
 * (OTP, rate limit) so raw emails/phones never leave the database.
 */
@Component
public class IdentifierHasher {

    private final String secret;

    public IdentifierHasher(AuthProperties properties) {
        this.secret = properties.otp().secret();
    }

    public String hash(Identifier identifier) {
        return Hashing.hmacSha256Hex(secret, "id:" + identifier.value());
    }
}
