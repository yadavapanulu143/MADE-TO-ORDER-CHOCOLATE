package com.choco.identity.application.command;

public record VerifyOtpCommand(String customerId, String channel, String code) {
}
