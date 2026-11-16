package com.choco.identity.application.command;

public record ResendOtpCommand(String customerId, String channel) {
}
