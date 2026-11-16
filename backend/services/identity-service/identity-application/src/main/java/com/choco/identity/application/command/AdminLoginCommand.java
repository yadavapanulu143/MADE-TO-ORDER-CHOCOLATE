package com.choco.identity.application.command;

/** An administrator signs in with an email address only; there is no mobile channel. */
public record AdminLoginCommand(String email, String password, String ipAddress, String userAgent) {
}
