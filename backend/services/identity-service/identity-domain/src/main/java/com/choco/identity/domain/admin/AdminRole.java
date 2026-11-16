package com.choco.identity.domain.admin;

/**
 * The role an administrator account holds.
 *
 * <p>Two levels, not a free-form set. {@code ADMIN} covers the day-to-day operator work — products,
 * orders, production, delivery, reports and the customer export. {@code SUPER_ADMIN} additionally
 * holds the actions that can move money or widen access: refunds, business settings and managing
 * other administrators. Keeping the levels here rather than as a bag of flags per row means an
 * account's authority is always one of two reviewable values.
 */
public enum AdminRole {

    ADMIN,
    SUPER_ADMIN;

    public boolean isSuperAdmin() {
        return this == SUPER_ADMIN;
    }
}
