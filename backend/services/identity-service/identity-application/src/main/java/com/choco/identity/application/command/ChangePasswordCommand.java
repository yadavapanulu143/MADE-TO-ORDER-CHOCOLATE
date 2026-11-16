package com.choco.identity.application.command;

public record ChangePasswordCommand(String customerId, String currentPassword, String newPassword) {
}
