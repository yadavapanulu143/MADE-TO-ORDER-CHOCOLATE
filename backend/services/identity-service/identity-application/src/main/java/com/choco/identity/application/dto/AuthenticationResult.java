package com.choco.identity.application.dto;

import com.choco.identity.application.port.TokenPair;

public record AuthenticationResult(CustomerView customer, TokenPair tokens) {
}
