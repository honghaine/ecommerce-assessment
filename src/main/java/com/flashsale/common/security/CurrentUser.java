package com.flashsale.common.security;

import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Authenticated principal extracted from the access token.
 */
public record CurrentUser(long id, String region, String role) {

    public static final String CLAIM_REGION = "region";
    public static final String CLAIM_ROLE = "role";

    public static CurrentUser from(Jwt jwt) {
        return new CurrentUser(Long.parseLong(jwt.getSubject()), jwt.getClaimAsString(CLAIM_REGION),
                jwt.getClaimAsString(CLAIM_ROLE));
    }
}
