package com.choco.identity.domain.customer;

/**
 * The two verification steps a customer must complete.
 *
 * <p>Both are mandatory before checkout. They are modelled as separate channels rather than a
 * single boolean because they can complete in either order, at different times, and the UI must
 * report which one is still outstanding.
 */
public enum VerificationChannel {
    EMAIL,
    MOBILE
}
