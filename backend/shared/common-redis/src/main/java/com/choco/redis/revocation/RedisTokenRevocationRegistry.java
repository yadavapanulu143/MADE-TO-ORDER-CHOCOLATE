package com.choco.redis.revocation;

import com.choco.core.port.TokenRevocationRegistry;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Redis-backed access-token revocation list.
 *
 * <p>Redis rather than a database table because this is read on <em>every</em> authenticated request:
 * a database round trip in the token path would put the identity service's table in front of every
 * call in the platform. Each entry is a key with a TTL, so it vanishes on its own and the store stays
 * proportional to the security events of the last token-lifetime window rather than to the number of
 * users.
 *
 * <p>Two key shapes, one per revocation scope. A session key holds the moment the session was revoked;
 * a subject key holds the cutoff before which that subject's tokens are refused. Both store an epoch
 * timestamp rather than a flag, so {@link #isRevoked} can answer "was this specific token issued
 * before the revocation?" — which is what makes a subject cutoff precise and lets a fresh sign-in
 * after a password change work.
 *
 * <p><b>Fail-closed on a store error.</b> If Redis is unreachable, {@link #isRevoked} throws rather
 * than returning {@code false}. Answering "not revoked" when the store cannot be consulted would
 * silently re-enable every logged-out session — precisely the failure this class exists to prevent. A
 * revoked token is a security decision, so an unavailable store must deny, not permit. The caller
 * (the revocation-aware decoder) turns that into a 401.
 */
public class RedisTokenRevocationRegistry implements TokenRevocationRegistry {

    private static final String SESSION_PREFIX = "revoked:session:";
    private static final String SUBJECT_PREFIX = "revoked:subject:";

    private final StringRedisTemplate redis;

    public RedisTokenRevocationRegistry(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public void revokeSession(String sessionId, Instant until) {
        // A session key is presence-based: the whole session is retired, so any token naming it is
        // refused regardless of when it was issued. The value is a placeholder; the TTL is what makes
        // the entry disappear once no token of the session could still be presented.
        put(SESSION_PREFIX + sessionId, until, "1");
    }

    @Override
    public void revokeSubjectIssuedBefore(UUID subject, Instant cutoff, Instant until) {
        // The stored value is the cutoff: a token issued before it is refused, one issued at or after
        // it is not. A later password change overwrites it with a newer cutoff, which is correct — the
        // newest change is the one that must hold.
        put(SUBJECT_PREFIX + subject, until, String.valueOf(cutoff.toEpochMilli()));
    }

    @Override
    public boolean isRevoked(String sessionId, UUID subject, Instant issuedAt) {
        // A missing key is the common case and means "not revoked". A store failure propagates: the
        // decoder must not treat an unanswerable question as a permission.
        if (Boolean.TRUE.equals(redis.hasKey(SESSION_PREFIX + sessionId))) {
            return true;
        }

        String cutoff = redis.opsForValue().get(SUBJECT_PREFIX + subject);
        return cutoff != null && issuedAt.isBefore(Instant.ofEpochMilli(Long.parseLong(cutoff)));
    }

    private void put(String key, Instant until, String value) {
        Duration ttl = Duration.between(Instant.now(), until);
        if (ttl.isNegative() || ttl.isZero()) {
            // The tokens this entry would cover are already past their own expiry; the signature check
            // rejects them and the record would only be dead weight.
            return;
        }
        redis.opsForValue().set(key, value, ttl);
    }
}
