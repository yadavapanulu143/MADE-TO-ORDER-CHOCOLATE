package com.choco.identity.application.dto;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * Read model returned about an administrator.
 *
 * <p>Contains no password hash and no token. Built explicitly rather than serialising the aggregate,
 * so a new field on {@code AdminAccount} cannot leak by default.
 */
public record AdminView(
        UUID id,
        String fullName,
        String email,
        String role,
        Set<String> permissions,
        boolean active,
        Instant createdAt) {
}
