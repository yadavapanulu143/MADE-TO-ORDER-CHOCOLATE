package com.choco.identity.application.command;

/** {@code identifier} is an email address or a mobile number. */
public record LoginCommand(String identifier, String password, String ipAddress, String userAgent) {
}
