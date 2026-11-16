package com.choco.identity.adapter.in.web;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Registration payload.
 *
 * <p>Bean Validation runs first so a malformed request is rejected before any business logic or
 * database access. The password bound is a maximum as well as a minimum: an unbounded password is a
 * denial-of-service vector, because hashing cost grows with input length.
 */
public record RegisterCustomerRequest(
        @NotBlank(message = "Full name is required")
        @Size(min = 2, max = 120, message = "Full name must be between 2 and 120 characters")
        String fullName,

        @NotBlank(message = "Email address is required")
        @Email(message = "Enter a valid email address")
        @Size(max = 254, message = "Email address is too long")
        String email,

        @NotBlank(message = "Mobile number is required")
        @Pattern(regexp = "^(\\+91|91|0)?[6-9][0-9]{9}$",
                message = "Enter a valid 10-digit Indian mobile number")
        String mobile,

        @NotBlank(message = "Password is required")
        @Size(min = 10, max = 128, message = "Password must be between 10 and 128 characters")
        String password) {
}
