package com.choco.identity.application.command;

/**
 * Changes a signed-in customer's password.
 *
 * <p>Carries no customer id: the caller's identity comes from the token, so a body cannot name
 * another account. The current password is required as proof that the person at the keyboard is the
 * account holder and not someone who merely found an unlocked session.
 */
public record ChangePasswordCommand(String currentPassword, String newPassword) {
}
