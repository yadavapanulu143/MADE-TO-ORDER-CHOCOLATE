package com.choco.core.port;

/**
 * Outbound port for rate limiting.
 *
 * <p>A port rather than a direct Redis call, so a use case can state "at most five attempts per hour"
 * without knowing that Redis exists, and so tests can substitute an in-memory counter instead of
 * standing up a server.
 *
 * <p>Every method returns a {@link Decision} rather than a boolean. The caller needs the retry delay
 * to answer with a useful {@code Retry-After} header, and an operator investigating a lockout needs
 * to know which limit was hit.
 */
public interface RateLimiter {

    /**
     * Records one attempt against {@code key} and reports whether it is allowed.
     *
     * <p>The counter starts on first use and expires after {@code windowSeconds} of inactivity, so an
     * idle key costs nothing and cannot accumulate forever.
     */
    Decision tryConsume(String key, int limit, long windowSeconds);

    /** Reads the current count without incrementing it. Intended for diagnostics and tests. */
    long currentCount(String key);

    /** Clears a key. Used when an action should forgive earlier attempts, such as a successful login. */
    void reset(String key);

    /**
     * Outcome of an attempt.
     *
     * @param allowed      whether the caller may proceed
     * @param remaining    attempts left in the window after this one
     * @param retryAfterSeconds seconds until the window resets; zero when allowed
     */
    record Decision(boolean allowed, long remaining, long retryAfterSeconds) {

        public static Decision allow(long remaining) {
            return new Decision(true, remaining, 0);
        }

        public static Decision deny(long retryAfterSeconds) {
            return new Decision(false, 0, Math.max(1, retryAfterSeconds));
        }
    }
}
