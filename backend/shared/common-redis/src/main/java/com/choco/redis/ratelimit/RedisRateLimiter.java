package com.choco.redis.ratelimit;

import com.choco.core.port.RateLimiter;
import java.util.List;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

/**
 * Redis-backed fixed-window rate limiter.
 *
 * <p>The increment and the limit check run inside one Lua script, and that is the whole point of the
 * class. A read-then-write pair issued from Java is two round trips with a gap between them: several
 * requests can read the same count, all conclude they are within the limit, and all be allowed. Redis
 * runs a script as a single atomic step, so exactly the allowed number of requests get through even
 * when they arrive at the same instant. For login brute-force protection, a limiter that can be
 * defeated by sending requests concurrently is worse than none, because it looks like a control.
 *
 * <p>Fixed windows rather than sliding: a fixed window can allow up to twice the limit across a
 * boundary, but it costs one key per subject, needs no sorted set, and for "five logins per minute"
 * the imprecision is irrelevant. The tighter bound would buy nothing here.
 *
 * <p>The counter key expires on its own, so an idle subject leaves no residue and the store cannot
 * grow without bound. The TTL is refreshed on each attempt, which means the window measures inactivity
 * rather than elapsed time since the first attempt — the behaviour wanted for a lockout that should
 * lift once the attacker stops.
 */
public class RedisRateLimiter implements RateLimiter {

    /**
     * Increments the counter, sets its expiry on first use, and returns both values.
     *
     * <p>{@code INCR} returns 1 for a brand-new key, which is the signal to apply the TTL; setting an
     * expiry on every call would keep pushing the window forward and a steady trickle of attempts
     * could hold a key open indefinitely.
     *
     * <p>Returns {count, ttlSeconds}. The TTL is -1 when the key has no expiry, which should not
     * happen here but is handled rather than assumed.
     */
    private static final DefaultRedisScript<List> CONSUME_SCRIPT = new DefaultRedisScript<>("""
            local count = redis.call('INCR', KEYS[1])
            if count == 1 then
              redis.call('EXPIRE', KEYS[1], ARGV[1])
            end
            local ttl = redis.call('TTL', KEYS[1])
            return {count, ttl}
            """, List.class);

    private final StringRedisTemplate redis;

    public RedisRateLimiter(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public Decision tryConsume(String key, int limit, long windowSeconds) {
        List<?> result = redis.execute(CONSUME_SCRIPT, List.of(key),
                String.valueOf(windowSeconds));
        if (result == null || result.size() < 2) {
            // Redis answered but the shape was unexpected. Failing open here would disable the
            // control silently; failing closed would lock every user out on a server quirk. The
            // exception surfaces the problem instead of picking either bad option.
            throw new IllegalStateException("Unexpected rate limiter script result: " + result);
        }

        long count = toLong(result.get(0));
        long ttl = toLong(result.get(1));

        if (count > limit) {
            // ttl is -1 for a key without expiry; treat that as one window so Retry-After is sane.
            return Decision.deny(ttl > 0 ? ttl : windowSeconds);
        }
        return Decision.allow(Math.max(0, limit - count));
    }

    @Override
    public long currentCount(String key) {
        String value = redis.opsForValue().get(key);
        return value == null ? 0 : toLong(value);
    }

    @Override
    public void reset(String key) {
        redis.delete(key);
    }

    private long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(String.valueOf(value));
    }
}
