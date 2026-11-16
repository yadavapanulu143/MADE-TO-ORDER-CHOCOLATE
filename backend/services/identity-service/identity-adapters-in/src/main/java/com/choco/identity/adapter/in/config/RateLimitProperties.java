package com.choco.identity.adapter.in.config;

import com.choco.identity.application.service.AbuseGuard;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for the authentication rate limits.
 *
 * <p>Every field is initialised from {@link AbuseGuard.Policy#defaults()}, so the shipped limits are
 * defined in exactly one place. This matters more than it looks: the previous arrangement repeated
 * each limit as a literal in a {@code @Value} default as well as in the policy, the two drifted, and
 * the value that actually took effect was the copy nobody was maintaining. An operator can still
 * override any field under {@code choco.security.rate-limit.*}, and that override now clearly replaces
 * a single documented default rather than shadowing a second one.
 *
 * <p>A class with setters rather than a record, because Spring Boot binds mutable configuration
 * objects and a record would need constructor binding plus a value for every field.
 */
@ConfigurationProperties(prefix = "choco.security.rate-limit")
public class RateLimitProperties {

    private int loginAttemptsPerIdentifier = AbuseGuard.Policy.defaults().loginAttemptsPerIdentifier();
    private int loginAttemptsPerIp = AbuseGuard.Policy.defaults().loginAttemptsPerIp();
    private Duration loginWindow = AbuseGuard.Policy.defaults().loginWindow();

    private int otpVerificationsPerCustomer = AbuseGuard.Policy.defaults().otpVerificationsPerCustomer();
    private Duration otpWindow = AbuseGuard.Policy.defaults().otpWindow();

    private int otpResendsPerCustomer = AbuseGuard.Policy.defaults().otpResendsPerCustomer();
    private Duration otpResendWindow = AbuseGuard.Policy.defaults().otpResendWindow();

    private int passwordResetsPerIdentifier = AbuseGuard.Policy.defaults().passwordResetsPerIdentifier();
    private int passwordResetsPerIp = AbuseGuard.Policy.defaults().passwordResetsPerIp();
    private Duration passwordResetWindow = AbuseGuard.Policy.defaults().passwordResetWindow();

    private int passwordChangesPerCustomer = AbuseGuard.Policy.defaults().passwordChangesPerCustomer();
    private Duration passwordChangeWindow = AbuseGuard.Policy.defaults().passwordChangeWindow();

    private int registrationsPerIp = AbuseGuard.Policy.defaults().registrationsPerIp();
    private Duration registrationWindow = AbuseGuard.Policy.defaults().registrationWindow();

    /** Converts the bound configuration into the policy the guard consumes. */
    public AbuseGuard.Policy toPolicy() {
        return new AbuseGuard.Policy(
                loginAttemptsPerIdentifier, loginAttemptsPerIp, loginWindow,
                otpVerificationsPerCustomer, otpWindow,
                otpResendsPerCustomer, otpResendWindow,
                passwordResetsPerIdentifier, passwordResetsPerIp, passwordResetWindow,
                passwordChangesPerCustomer, passwordChangeWindow,
                registrationsPerIp, registrationWindow);
    }

    public int getLoginAttemptsPerIdentifier() {
        return loginAttemptsPerIdentifier;
    }

    public void setLoginAttemptsPerIdentifier(int loginAttemptsPerIdentifier) {
        this.loginAttemptsPerIdentifier = loginAttemptsPerIdentifier;
    }

    public int getLoginAttemptsPerIp() {
        return loginAttemptsPerIp;
    }

    public void setLoginAttemptsPerIp(int loginAttemptsPerIp) {
        this.loginAttemptsPerIp = loginAttemptsPerIp;
    }

    public Duration getLoginWindow() {
        return loginWindow;
    }

    public void setLoginWindow(Duration loginWindow) {
        this.loginWindow = loginWindow;
    }

    public int getOtpVerificationsPerCustomer() {
        return otpVerificationsPerCustomer;
    }

    public void setOtpVerificationsPerCustomer(int otpVerificationsPerCustomer) {
        this.otpVerificationsPerCustomer = otpVerificationsPerCustomer;
    }

    public Duration getOtpWindow() {
        return otpWindow;
    }

    public void setOtpWindow(Duration otpWindow) {
        this.otpWindow = otpWindow;
    }

    public int getOtpResendsPerCustomer() {
        return otpResendsPerCustomer;
    }

    public void setOtpResendsPerCustomer(int otpResendsPerCustomer) {
        this.otpResendsPerCustomer = otpResendsPerCustomer;
    }

    public Duration getOtpResendWindow() {
        return otpResendWindow;
    }

    public void setOtpResendWindow(Duration otpResendWindow) {
        this.otpResendWindow = otpResendWindow;
    }

    public int getPasswordResetsPerIdentifier() {
        return passwordResetsPerIdentifier;
    }

    public void setPasswordResetsPerIdentifier(int passwordResetsPerIdentifier) {
        this.passwordResetsPerIdentifier = passwordResetsPerIdentifier;
    }

    public int getPasswordResetsPerIp() {
        return passwordResetsPerIp;
    }

    public void setPasswordResetsPerIp(int passwordResetsPerIp) {
        this.passwordResetsPerIp = passwordResetsPerIp;
    }

    public Duration getPasswordResetWindow() {
        return passwordResetWindow;
    }

    public void setPasswordResetWindow(Duration passwordResetWindow) {
        this.passwordResetWindow = passwordResetWindow;
    }

    public int getPasswordChangesPerCustomer() {
        return passwordChangesPerCustomer;
    }

    public void setPasswordChangesPerCustomer(int passwordChangesPerCustomer) {
        this.passwordChangesPerCustomer = passwordChangesPerCustomer;
    }

    public Duration getPasswordChangeWindow() {
        return passwordChangeWindow;
    }

    public void setPasswordChangeWindow(Duration passwordChangeWindow) {
        this.passwordChangeWindow = passwordChangeWindow;
    }

    public int getRegistrationsPerIp() {
        return registrationsPerIp;
    }

    public void setRegistrationsPerIp(int registrationsPerIp) {
        this.registrationsPerIp = registrationsPerIp;
    }

    public Duration getRegistrationWindow() {
        return registrationWindow;
    }

    public void setRegistrationWindow(Duration registrationWindow) {
        this.registrationWindow = registrationWindow;
    }
}
