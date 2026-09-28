package com.flashsale.auth.service;

import org.springframework.security.oauth2.jwt.Jwt;

import com.flashsale.auth.model.TokenPair;
import com.flashsale.user.entity.User;

/**
 * Short-lived JWT access tokens + rotating opaque refresh tokens.
 */
public interface TokenService {

    TokenPair issue(User user);

    /** Rotates a refresh token; re-use of a rotated token revokes all sessions of the user. */
    TokenPair refresh(String rawRefreshToken);

    /** Blacklists the access token until expiry and revokes the refresh token if given. */
    void revoke(Jwt accessToken, String rawRefreshToken);
}
