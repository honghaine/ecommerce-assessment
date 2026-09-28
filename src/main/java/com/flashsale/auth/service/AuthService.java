package com.flashsale.auth.service;

import org.springframework.security.oauth2.jwt.Jwt;

import com.flashsale.auth.model.TokenPair;

/**
 * Register / verify / login / refresh / logout for email and phone identifiers.
 */
public interface AuthService {

    /** Creates a PENDING buyer and sends an OTP. Answers identically whether or not the identifier exists. */
    void register(String rawIdentifier, String password, String rawRegion, String clientIp);

    /** Sends a fresh OTP to a PENDING account (silently ignored otherwise). */
    void resendOtp(String rawIdentifier, String clientIp);

    /** Activates the account if the code is valid. */
    void verifyOtp(String rawIdentifier, String code, String clientIp);

    TokenPair login(String rawIdentifier, String password, String clientIp);

    TokenPair refresh(String refreshToken, String clientIp);

    void logout(Jwt accessToken, String refreshToken);
}
