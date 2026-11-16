package com.choco.identity.application.port;

import java.time.Instant;

public record IssuedToken(String value, Instant expiresAt) {
}
