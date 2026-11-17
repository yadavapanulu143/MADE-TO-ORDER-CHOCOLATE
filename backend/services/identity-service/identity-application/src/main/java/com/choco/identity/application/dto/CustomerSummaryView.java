package com.choco.identity.application.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * A row in the seller's customer list.
 *
 * <p>Deliberately narrower than {@link CustomerView}: the list is paged and holds many customers, so
 * it omits the saved addresses and any field that is not needed to recognise an account. Nothing here
 * can carry a password hash, a token or an OTP — those never leave the domain — and this record has no
 * field that could.
 */
public record CustomerSummaryView(
        UUID id,
        String fullName,
        String email,
        String mobile,
        boolean emailVerified,
        boolean mobileVerified,
        boolean fullyVerified,
        boolean active,
        Instant registeredAt) {
}
