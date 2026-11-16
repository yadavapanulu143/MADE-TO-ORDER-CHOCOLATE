package com.choco.identity.application.command;

/** Input for customer self-registration. */
public record RegisterCustomerCommand(
        String fullName,
        String email,
        String mobile,
        String password) {
}
