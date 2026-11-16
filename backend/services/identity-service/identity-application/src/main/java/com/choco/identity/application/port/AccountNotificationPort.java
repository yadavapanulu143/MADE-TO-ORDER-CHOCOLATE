package com.choco.identity.application.port;

/** Outbound port for non-OTP account messages (welcome, password changed, password reset requested). */
public interface AccountNotificationPort {

    void welcome(String email, String fullName);

    void passwordChanged(String email, String fullName);

    void passwordResetRequested(String email, String fullName, String resetToken);
}
