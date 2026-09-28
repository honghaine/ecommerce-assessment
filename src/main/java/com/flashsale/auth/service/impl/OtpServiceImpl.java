package com.flashsale.auth.service.impl;

import java.security.SecureRandom;
import java.util.List;
import java.util.Optional;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import com.flashsale.auth.config.AuthProperties;
import com.flashsale.auth.model.Identifier;
import com.flashsale.auth.model.OtpPurpose;
import com.flashsale.auth.service.OtpService;
import com.flashsale.auth.support.Hashing;
import com.flashsale.auth.support.IdentifierHasher;

/**
 * One-time codes stored in Redis (shared by all instances):
 * <ul>
 *   <li>only an HMAC of the code is stored, keyed by a hash of the identifier;</li>
 *   <li>TTL + max attempts; verification is atomic and single-use (Lua);</li>
 *   <li>resend cooldown per identifier.</li>
 * </ul>
 * With {@code app.auth.otp.plain-storage=true} (dev/test only) the code and identifier are stored
 * as-is: {@code otp:register:buyer@example.com → {code: "123456", attempts: 0}}.
 */
@Slf4j
@Service
public class OtpServiceImpl implements OtpService {

    private static final RedisScript<Long> STORE_SCRIPT = RedisScript.of("""
            redis.call('DEL', KEYS[1])
            redis.call('HSET', KEYS[1], 'code', ARGV[1], 'attempts', 0)
            redis.call('PEXPIRE', KEYS[1], ARGV[2])
            return 1
            """, Long.class);

    /** 1 = valid (code consumed), 0 = invalid/expired/too many attempts. */
    private static final RedisScript<Long> VERIFY_SCRIPT = RedisScript.of("""
            local stored = redis.call('HGET', KEYS[1], 'code')
            if not stored then return 0 end
            local attempts = redis.call('HINCRBY', KEYS[1], 'attempts', 1)
            if attempts > tonumber(ARGV[2]) then
              redis.call('DEL', KEYS[1])
              return 0
            end
            if stored == ARGV[1] then
              redis.call('DEL', KEYS[1])
              return 1
            end
            if attempts >= tonumber(ARGV[2]) then redis.call('DEL', KEYS[1]) end
            return 0
            """, Long.class);

    private final StringRedisTemplate redis;
    private final AuthProperties.Otp properties;
    private final IdentifierHasher identifierHasher;
    private final SecureRandom random = new SecureRandom();

    public OtpServiceImpl(StringRedisTemplate redis, AuthProperties authProperties, IdentifierHasher identifierHasher) {
        this.redis = redis;
        this.properties = authProperties.otp();
        this.identifierHasher = identifierHasher;
        if (properties.plainStorage()) {
            log.warn("OTP plain storage is ENABLED — codes are stored unhashed in Redis. Never use in production.");
        }
    }

    /**
     * Issues a new code, replacing any previous one.
     *
     * @return the plain code to deliver, or empty if still in resend cooldown
     */
    @Override
    public Optional<String> issue(OtpPurpose purpose, Identifier identifier) {
        String idKey = identifierKey(identifier);
        Boolean allowed = redis.opsForValue()
                .setIfAbsent(cooldownKey(purpose, idKey), "1", properties.resendCooldown());
        if (!Boolean.TRUE.equals(allowed)) {
            return Optional.empty();
        }
        String code = generateCode();
        redis.execute(STORE_SCRIPT, List.of(codeKey(purpose, idKey)),
                storedCode(purpose, identifier, code), String.valueOf(properties.ttl().toMillis()));
        return Optional.of(code);
    }

    @Override
    public boolean verify(OtpPurpose purpose, Identifier identifier, String code) {
        Long result = redis.execute(VERIFY_SCRIPT, List.of(codeKey(purpose, identifierKey(identifier))),
                storedCode(purpose, identifier, code), String.valueOf(properties.maxAttempts()));
        return Long.valueOf(1).equals(result);
    }

    @Override
    public long ttlSeconds() {
        return properties.ttl().toSeconds();
    }

    private String generateCode() {
        StringBuilder code = new StringBuilder(properties.length());
        for (int i = 0; i < properties.length(); i++) {
            code.append(random.nextInt(10));
        }
        return code.toString();
    }

    /**
     * Value stored in Redis and compared on verify. Hashed mode binds the code to purpose + identifier
     * so it cannot be replayed elsewhere; the key already scopes it in plain mode.
     */
    private String storedCode(OtpPurpose purpose, Identifier identifier, String code) {
        if (properties.plainStorage()) {
            return code;
        }
        return Hashing.hmacSha256Hex(properties.secret(), purpose + ":" + identifier.value() + ":" + code);
    }

    private String identifierKey(Identifier identifier) {
        return properties.plainStorage() ? identifier.value() : identifierHasher.hash(identifier);
    }

    private static String codeKey(OtpPurpose purpose, String idKey) {
        return "otp:" + purpose.name().toLowerCase() + ":" + idKey;
    }

    private static String cooldownKey(OtpPurpose purpose, String idKey) {
        return "otp:cooldown:" + purpose.name().toLowerCase() + ":" + idKey;
    }
}
