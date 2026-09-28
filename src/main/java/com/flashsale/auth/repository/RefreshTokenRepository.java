package com.flashsale.auth.repository;

import java.time.Instant;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import com.flashsale.auth.entity.RefreshToken;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /**
     * Atomically consumes an active token: of N concurrent refreshes with the same
     * token, exactly one gets {@code 1}.
     */
    @Modifying
    @Query("""
            update RefreshToken t set t.revokedAt = :now
            where t.tokenHash = :tokenHash and t.revokedAt is null and t.expiresAt > :now
            """)
    int revokeIfActive(String tokenHash, Instant now);

    @Modifying
    @Query("""
            update RefreshToken t set t.revokedAt = :now
            where t.tokenHash = :tokenHash and t.userId = :userId and t.revokedAt is null
            """)
    int revokeForUser(String tokenHash, long userId, Instant now);

    @Modifying
    @Query("update RefreshToken t set t.revokedAt = :now where t.userId = :userId and t.revokedAt is null")
    int revokeAllForUser(long userId, Instant now);
}
