package com.choco.identity.adapter.in.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record VerifyOtpRequest(
        @NotBlank(message = "Verification code is required")
        @Pattern(regexp = "[0-9]{6}", message = "The verification code must be 6 digits")
        String code) {
}
