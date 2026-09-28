package com.flashsale.auth.service;

import java.util.Optional;

import com.flashsale.auth.model.Identifier;
import com.flashsale.auth.model.OtpPurpose;

/**
 * One-time codes for identifier verification.
 */
public interface OtpService {

    /** @return the plain code to deliver, or empty while in resend cooldown */
    Optional<String> issue(OtpPurpose purpose, Identifier identifier);

    /** Single-use: a valid code is consumed. */
    boolean verify(OtpPurpose purpose, Identifier identifier, String code);

    long ttlSeconds();
}
