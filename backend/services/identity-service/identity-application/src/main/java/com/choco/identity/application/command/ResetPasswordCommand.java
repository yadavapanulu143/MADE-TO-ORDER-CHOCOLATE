package com.choco.identity.application.command;

public record ResetPasswordCommand(String token, String newPassword) {
}
