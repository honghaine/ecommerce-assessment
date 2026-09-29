package com.flashsale.flashsale.support;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import com.flashsale.flashsale.model.ItemSnapshot;

/**
 * Redis pre-filter in front of the purchase transaction, shared by all instances. One Lua script
 * atomically checks "already bought today" + remaining stock and reserves both. It only sheds load —
 * the database constraints stay the source of truth, and Redis errors fall back to the DB path.
 *
 * <p>Keys use the {region} hash tag so both keys of one market live in the same Redis Cluster slot.
 */
@Slf4j
@Component
public class StockGate {

    public enum Result { PASSED, SOLD_OUT, ALREADY_PURCHASED, BYPASSED }

    private static final long OK = 1, SOLD_OUT = -1, ALREADY = -2, NOT_INITIALISED = -3;
    private static final Duration KEY_GRACE = Duration.ofHours(1);

    private static final RedisScript<Long> ACQUIRE = RedisScript.of("""
            if redis.call('EXISTS', KEYS[2]) == 1 then return -2 end
            local stock = redis.call('GET', KEYS[1])
            if not stock then return -3 end
            if tonumber(stock) <= 0 then return -1 end
            redis.call('DECR', KEYS[1])
            redis.call('SET', KEYS[2], ARGV[1], 'PX', ARGV[2])
            return 1
            """, Long.class);

    private final StringRedisTemplate redis;

    public StockGate(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public Result tryAcquire(ItemSnapshot item, long userId, ZoneId zone) {
        try {
            String stockKey = stockKey(item.region(), item.itemId());
            String userKey = userKey(item.region(), userId, item.saleDate());
            List<String> keys = List.of(stockKey, userKey);
            String userTtl = String.valueOf(untilEndOfDay(item.saleDate(), zone).toMillis());
            String itemId = String.valueOf(item.itemId());

            Long result = redis.execute(ACQUIRE, keys, itemId, userTtl);
            if (result != null && result == NOT_INITIALISED) {
                initStock(item);
                result = redis.execute(ACQUIRE, keys, itemId, userTtl);
            }
            if (result == null) {
                return Result.BYPASSED;
            }
            if (result == OK) {
                return Result.PASSED;
            }
            return result == ALREADY ? Result.ALREADY_PURCHASED : Result.SOLD_OUT;
        } catch (DataAccessException ex) {
            log.warn("Stock gate unavailable, falling back to database checks: {}", ex.getMessage());
            return Result.BYPASSED;
        }
    }

    /** Undo after the DB rejected a request that passed the gate. */
    public void compensate(ItemSnapshot item, long userId, Compensation compensation) {
        try {
            String stockKey = stockKey(item.region(), item.itemId());
            switch (compensation) {
                case SOLD_OUT -> redis.opsForValue().set(stockKey, "0", untilSessionEnd(item));
                case ALREADY_PURCHASED -> redis.opsForValue().increment(stockKey);
                case RELEASE -> {
                    redis.opsForValue().increment(stockKey);
                    redis.delete(userKey(item.region(), userId, item.saleDate()));
                }
            }
        } catch (DataAccessException ex) {
            log.warn("Stock gate compensation failed for item {} (reconciler will fix): {}", item.itemId(),
                    ex.getMessage());
        }
    }

    /** Sets the counter from DB truth ({@code quota - sold}); used by lazy init and the reconciler. */
    public void resetStock(ItemSnapshot item) {
        redis.opsForValue().set(stockKey(item.region(), item.itemId()), String.valueOf(item.remaining()),
                untilSessionEnd(item));
    }

    private void initStock(ItemSnapshot item) {
        redis.opsForValue().setIfAbsent(stockKey(item.region(), item.itemId()), String.valueOf(item.remaining()),
                untilSessionEnd(item));
    }

    public enum Compensation { SOLD_OUT, ALREADY_PURCHASED, RELEASE }

    static String stockKey(String region, long itemId) {
        return "fs:{" + region + "}:stock:" + itemId;
    }

    static String userKey(String region, long userId, LocalDate saleDate) {
        return "fs:{" + region + "}:user:" + userId + ":" + saleDate;
    }

    private static Duration untilSessionEnd(ItemSnapshot item) {
        return positive(Duration.between(Instant.now(), item.endAt()).plus(KEY_GRACE));
    }

    private static Duration untilEndOfDay(LocalDate day, ZoneId zone) {
        Instant endOfDay = day.plusDays(1).atStartOfDay(zone).toInstant();
        return positive(Duration.between(Instant.now(), endOfDay).plus(KEY_GRACE));
    }

    private static Duration positive(Duration duration) {
        return duration.isNegative() || duration.isZero() ? KEY_GRACE : duration;
    }
}
