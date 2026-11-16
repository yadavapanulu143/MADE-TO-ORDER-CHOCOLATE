package com.choco.identity.application.command;

/** Input for customer self-registration. */
public record RegisterCustomerCommand(
        String fullName,
        String email,
        String mobile,
        String password,
        String ipAddress) {

    /** Convenience for callers with no source address, such as internal jobs and tests. */
    public RegisterCustomerCommand(String fullName, String email, String mobile, String password) {
        this(fullName, email, mobile, password, null);
    }
}
