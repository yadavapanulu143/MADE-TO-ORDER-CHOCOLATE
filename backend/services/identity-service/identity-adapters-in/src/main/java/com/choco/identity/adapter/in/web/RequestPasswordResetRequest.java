package com.choco.identity.adapter.in.web;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record RequestPasswordResetRequest(
        @NotBlank(message = "Email address is required")
        @Email(message = "Enter a valid email address")
        String email) {
}
