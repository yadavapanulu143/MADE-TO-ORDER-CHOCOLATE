package com.choco.identity.adapter.in.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ResendOtpRequest(
        @NotBlank(message = "Channel is required")
        @Pattern(regexp = "(?i)EMAIL|MOBILE", message = "Channel must be EMAIL or MOBILE")
        String channel) {
}
