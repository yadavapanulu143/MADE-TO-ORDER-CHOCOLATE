package com.choco.security.principal;

import java.util.Set;
import java.util.UUID;

/**
 * The caller as seen by every service: an id, a role, the verification flags and granted
 * permissions. It deliberately carries no password, hash or OTP.
 */
public record AuthenticatedUser(
        UUID userId,
        String email,
        String role,
        boolean emailVerified,
        boolean mobileVerified,
        Set<String> permissions) {

    public AuthenticatedUser {
        permissions = permissions == null ? Set.of() : Set.copyOf(permissions);
    }

    public boolean isAdmin() {
        return Roles.ADMIN.equals(role) || Roles.SUPER_ADMIN.equals(role);
    }

    public boolean isSuperAdmin() {
        return Roles.SUPER_ADMIN.equals(role);
    }

    public boolean isCustomer() {
        return Roles.CUSTOMER.equals(role);
    }

    public boolean fullyVerified() {
        return emailVerified && mobileVerified;
    }

    public boolean hasPermission(String permission) {
        return isSuperAdmin() || permissions.contains(permission);
    }
}
