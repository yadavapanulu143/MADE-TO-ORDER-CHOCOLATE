package com.choco.identity.application.fake;

import com.choco.identity.application.port.OpaqueTokenHasher;
import com.choco.identity.application.service.AbuseGuard;
import com.choco.test.Fakes;

/**
 * Builds an {@link AbuseGuard} for tests.
 *
 * <p>The guard needs a rate limiter and a key hasher, and every service under test now takes one.
 * Constructing it in a single place keeps the tests readable and means a change to the guard's
 * dependencies is one edit rather than six.
 *
 * <p>The limiter is in-memory and the hasher is identity-like but still deterministic, which is all
 * the guard requires: it hashes identifiers only so customer addresses do not appear in Redis keys,
 * and a test asserts on behaviour, not on the hash value.
 */
public final class TestAbuseGuard {

    private TestAbuseGuard() {
    }

    /** A guard with the shipped default limits and a fresh in-memory limiter. */
    public static AbuseGuard create() {
        return new AbuseGuard(new Fakes.InMemoryRateLimiter(), new IdentityHasher(),
                AbuseGuard.Policy.defaults());
    }

    /** A guard with the default limits sharing the given limiter, for tests that inspect counters. */
    public static AbuseGuard create(Fakes.InMemoryRateLimiter limiter) {
        return new AbuseGuard(limiter, new IdentityHasher(), AbuseGuard.Policy.defaults());
    }

    /** A guard with limits chosen by the test, for tests that need to hit a limit quickly. */
    public static AbuseGuard withPolicy(AbuseGuard.Policy policy) {
        return new AbuseGuard(new Fakes.InMemoryRateLimiter(), new IdentityHasher(), policy);
    }

    private static final class IdentityHasher implements OpaqueTokenHasher {
        @Override
        public String hash(String value) {
            return "hash:" + value;
        }
    }
}
