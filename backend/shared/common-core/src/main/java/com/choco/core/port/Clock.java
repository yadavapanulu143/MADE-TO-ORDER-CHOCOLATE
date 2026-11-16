package com.choco.core.port;

import java.time.Instant;

/**
 * Time is an injected dependency, not a static call, so OTP expiry, token lifetime and
 * delivery-timestamp logic can be tested deterministically instead of with sleeps.
 */
@FunctionalInterface
public interface Clock {

    Instant now();

    static Clock system() {
        return Instant::now;
    }
}
