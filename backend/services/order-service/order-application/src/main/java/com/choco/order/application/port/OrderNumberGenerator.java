package com.choco.order.application.port;

import java.time.Instant;

/**
 * Produces the human-readable order number shown to the customer.
 *
 * <p>A port rather than a static formatter so the format is a single decision and tests can make it
 * deterministic. The number is a convenience for support conversations; the order id remains the
 * identifier everything else keys on, so a collision in the display number is a cosmetic problem, not
 * a correctness one — but the adapter still uses a sequence to avoid it.
 */
public interface OrderNumberGenerator {

    String next(Instant now);
}
