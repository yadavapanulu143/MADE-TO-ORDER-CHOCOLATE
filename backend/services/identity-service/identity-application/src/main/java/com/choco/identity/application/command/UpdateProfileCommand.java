package com.choco.identity.application.command;

/**
 * Changes a customer's display name.
 *
 * <p>Carries no customer id for the same reason as {@link CreateAddressCommand}: the caller's
 * identity comes from the token, so a body cannot name another account.
 *
 * <p>Only the name. Email and mobile are the account's verified identity and are changed through
 * {@link ChangeContactCommand}, which re-runs verification.
 */
public record UpdateProfileCommand(String fullName) {
}
