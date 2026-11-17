package com.choco.reporting.domain.model;

import java.time.Instant;

/**
 * The reporting view of a customer, projected from the identity service's events.
 *
 * <p>Holds no credential of any kind — no password, hash, OTP or token — only the identity facts the
 * seller reports on: that a customer registered and whether both contact channels are verified. The
 * email is masked before it reaches any export.
 */
public record CustomerProjection(
        String customerId,
        String fullName,
        String email,
        String mobile,
        boolean emailVerified,
        boolean mobileVerified,
        Instant registeredAt,
        Instant verifiedAt) {
}
