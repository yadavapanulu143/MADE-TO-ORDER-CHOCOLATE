package com.choco.identity.adapter.in.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** An administrator signs in with an email address only; there is no mobile channel. */
public record AdminLoginRequest(
        @NotBlank(message = "Email address is required")
        @Size(max = 254, message = "Email address is too long")
        String email,

        @NotBlank(message = "Password is required")
        @Size(max = 128, message = "Password is too long")
        String password) {
}
