package com.flashsale.auth.service.impl;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.flashsale.auth.config.AuthProperties;
import com.flashsale.auth.entity.RefreshToken;
import com.flashsale.auth.model.TokenPair;
import com.flashsale.auth.repository.RefreshTokenRepository;
import com.flashsale.auth.service.TokenService;
import com.flashsale.auth.support.Hashing;
import com.flashsale.auth.support.JwtBlacklist;
import com.flashsale.common.error.ApiException;
import com.flashsale.common.error.ErrorCode;
import com.flashsale.common.security.CurrentUser;
import com.flashsale.user.entity.User;
import com.flashsale.user.repository.UserRepository;

/**
 * Short-lived JWT access tokens + rotating opaque refresh tokens.
 */
@Slf4j
@Service
public class TokenServiceImpl implements TokenService {

    private static final int REFRESH_TOKEN_BYTES = 32;

    private final JwtEncoder jwtEncoder;
    private final JwtBlacklist blacklist;
    private final RefreshTokenRepository refreshTokens;
    private final UserRepository users;
    private final AuthProperties properties;
    private final SecureRandom random = new SecureRandom();

    public TokenServiceImpl(JwtEncoder jwtEncoder, JwtBlacklist blacklist, RefreshTokenRepository refreshTokens,
                        UserRepository users, AuthProperties properties) {
        this.jwtEncoder = jwtEncoder;
        this.blacklist = blacklist;
        this.refreshTokens = refreshTokens;
        this.users = users;
        this.properties = properties;
    }

    @Override
    @Transactional
    public TokenPair issue(User user) {
        Instant now = Instant.now();
        Instant accessExpiresAt = now.plus(properties.jwt().accessTokenTtl());
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(properties.jwt().issuer())
                .subject(String.valueOf(user.getId()))
                .issuedAt(now)
                .expiresAt(accessExpiresAt)
                .id(UUID.randomUUID().toString())
                .claim(CurrentUser.CLAIM_REGION, user.getRegion())
                .claim(CurrentUser.CLAIM_ROLE, user.getRole().name())
                .build();
        String accessToken = jwtEncoder.encode(
                JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();

        String refreshToken = newOpaqueToken();
        Instant refreshExpiresAt = now.plus(properties.refreshTokenTtl());
        refreshTokens.save(RefreshToken.issue(user.getId(), user.getRegion(), Hashing.sha256Hex(refreshToken),
                refreshExpiresAt));

        return new TokenPair(accessToken, properties.jwt().accessTokenTtl().toSeconds(), refreshToken,
                properties.refreshTokenTtl().toSeconds());
    }

    /**
     * Rotates a refresh token. Presenting an already-used token is treated as theft:
     * every refresh token of that user is revoked (commit kept despite the error).
     */
    @Override
    @Transactional(noRollbackFor = ApiException.class)
    public TokenPair refresh(String rawRefreshToken) {
        String hash = Hashing.sha256Hex(rawRefreshToken);
        Instant now = Instant.now();
        if (refreshTokens.revokeIfActive(hash, now) == 0) {
            refreshTokens.findByTokenHash(hash)
                    .filter(RefreshToken::isRevoked)
                    .ifPresent(reused -> {
                        log.warn("Refresh token reuse detected for user {}; revoking all sessions", reused.getUserId());
                        refreshTokens.revokeAllForUser(reused.getUserId(), now);
                    });
            throw new ApiException(ErrorCode.INVALID_REFRESH_TOKEN);
        }
        RefreshToken consumed = refreshTokens.findByTokenHash(hash)
                .orElseThrow(() -> new ApiException(ErrorCode.INVALID_REFRESH_TOKEN));
        User user = users.findById(consumed.getUserId())
                .filter(User::isActive)
                .orElseThrow(() -> new ApiException(ErrorCode.INVALID_REFRESH_TOKEN));
        return issue(user);
    }

    /** Logout: access token is blacklisted until expiry; refresh token (if given) is revoked. */
    @Override
    @Transactional
    public void revoke(Jwt accessToken, String rawRefreshToken) {
        blacklist.revoke(accessToken.getId(), accessToken.getExpiresAt());
        if (rawRefreshToken != null && !rawRefreshToken.isBlank()) {
            refreshTokens.revokeForUser(Hashing.sha256Hex(rawRefreshToken), CurrentUser.from(accessToken).id(),
                    Instant.now());
        }
    }

    private String newOpaqueToken() {
        byte[] bytes = new byte[REFRESH_TOKEN_BYTES];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
