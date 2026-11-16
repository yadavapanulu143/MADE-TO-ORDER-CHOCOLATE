package com.choco.identity.adapter.in.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request body for a password change.
 *
 * <p>The password length is bounded here only to reject an obviously malformed body early. The real
 * strength rules live in the shared password policy, which the service applies, so the two cannot
 * disagree about what a strong password is.
 */
public record ChangePasswordRequest(
        @NotBlank(message = "Your current password is required")
        String currentPassword,

        @NotBlank(message = "A new password is required")
        @Size(min = 12, max = 128, message = "Password must be between 12 and 128 characters")
        String newPassword) {
}
