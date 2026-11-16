package com.choco.identity.application.command;

/**
 * Changes a customer's email address or mobile number.
 *
 * <p>A dedicated command rather than extra fields on {@link UpdateProfileCommand}, because a contact
 * change is a different operation with different consequences: it revokes the verification for the
 * changed channel and triggers a new code. Keeping it separate means a name-only update cannot
 * accidentally disturb verification state.
 *
 * <p>Both fields are optional; a null means "leave this one unchanged". At least one must be present,
 * which the service enforces.
 */
public record ChangeContactCommand(String email, String mobile) {
}
