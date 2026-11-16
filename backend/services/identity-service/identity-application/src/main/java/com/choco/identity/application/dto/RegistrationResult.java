package com.choco.identity.application.dto;

import java.util.UUID;

/**
 * Returned after registration.
 *
 * <p>Deliberately does not include tokens: the customer must verify both channels before an
 * authenticated session is issued.
 */
public record RegistrationResult(UUID customerId, String email, String mobile) {
}
