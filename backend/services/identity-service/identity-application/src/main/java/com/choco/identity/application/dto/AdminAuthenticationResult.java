package com.choco.identity.application.dto;

import com.choco.identity.application.port.TokenPair;

/** The result of an administrator sign-in: who they are, and their token pair. */
public record AdminAuthenticationResult(AdminView admin, TokenPair tokens) {
}
