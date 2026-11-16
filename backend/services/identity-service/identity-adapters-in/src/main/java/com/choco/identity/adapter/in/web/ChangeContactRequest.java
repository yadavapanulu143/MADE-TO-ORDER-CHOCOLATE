package com.choco.identity.adapter.in.web;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request body for a contact change.
 *
 * <p>Both fields are optional and a null means "leave unchanged". Bean Validation only fires on
 * non-null values, so the constraints here check format and the service decides whether at least one
 * field was actually supplied.
 */
public record ChangeContactRequest(
        @Email(message = "Enter a valid email address")
        @Size(max = 254, message = "Email address is too long")
        String email,

        @Pattern(regexp = "[6-9][0-9]{9}", message = "Enter a valid 10-digit Indian mobile number")
        String mobile) {
}
