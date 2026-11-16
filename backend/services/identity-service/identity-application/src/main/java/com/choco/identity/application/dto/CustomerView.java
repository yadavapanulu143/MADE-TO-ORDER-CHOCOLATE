package com.choco.identity.application.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Read model returned to a customer about themselves.
 *
 * <p>Contains no password hash, no OTP and no token. The application layer builds this explicitly
 * rather than serialising the aggregate, so a new field on {@code Customer} cannot leak by default.
 */
public record CustomerView(
        UUID id,
        String fullName,
        String email,
        String mobile,
        boolean emailVerified,
        boolean mobileVerified,
        boolean fullyVerified,
        boolean active,
        Instant registeredAt,
        List<AddressView> addresses) {
}
