package com.choco.identity.application.service;

import com.choco.core.error.RateLimitedException;
import com.choco.core.port.RateLimiter;
import com.choco.identity.application.port.OpaqueTokenHasher;
import java.time.Duration;

/**
 * Applies the authentication rate limits.
 *
 * <p>Kept in the application layer rather than as an HTTP filter for two reasons. The policy is a
 * business decision — how many wrong passwords before a lockout — not a transport detail. And a
 * filter would only protect the web path, while these use cases will also be reachable from message
 * consumers and scheduled jobs, which would then run with no limit at all.
 *
 * <p><b>Two keys per action, not one.</b> Login is limited per account <em>and</em> per source
 * address. Limiting only the account lets an attacker spray one common password across many accounts;
 * limiting only the address lets a distributed attacker hammer a single account from many hosts.
 * Neither key alone is sufficient, and the combination is what the requirement asks for.
 *
 * <p><b>Identifiers are hashed before they become keys.</b> An email address in a Redis key is
 * personal data sitting in a store that is backed up, replicated and often exposed to operators, and
 * Redis keys also flow into metrics and slow-log output where nobody expects to find customer data.
 * Hashing keeps the limit working — the same address always produces the same key — without storing
 * the address itself.
 *
 * <p><b>The limits are checked before the password is.</b> That ordering is deliberate: verifying
 * first would spend an Argon2id hash on every request in a flood, so the flood itself would be the
 * denial of service. Checking first means a locked-out attacker costs one Redis round trip.
 *
 * <p><b>No account-enumeration oracle.</b> The counters are consumed for every attempt, whether or
 * not the account exists, so a 429 tells the attacker nothing about whether the address is
 * registered.
 */
public class AbuseGuard {

    /** How the limits are configured. Defaults are the values shipped unless overridden. */
    public record Policy(int loginAttemptsPerIdentifier,
                         int loginAttemptsPerIp,
                         Duration loginWindow,
                         int otpVerificationsPerCustomer,
                         Duration otpWindow,
                         int otpResendsPerCustomer,
                         Duration otpResendWindow,
                         int passwordResetsPerIdentifier,
                         int passwordResetsPerIp,
                         Duration passwordResetWindow,
                         int passwordChangesPerCustomer,
                         Duration passwordChangeWindow,
                         int registrationsPerIp,
                         Duration registrationWindow) {

        /**
         * Defaults sized for a single-seller chocolate business.
         *
         * <p>The login window is generous because a real customer mistyping a password twice must not
         * be locked out, while ten failures in fifteen minutes is far below what a brute-force attempt
         * needs. The per-address limits are higher than the per-account ones so that a shared
         * connection — an office or a mobile carrier's NAT — is not locked out by one person's
         * mistakes.
         *
         * <p><b>The per-identifier login limit deliberately sits above the account lockout
         * threshold</b> ({@code Customer.MAX_FAILED_LOGIN_ATTEMPTS}, currently 5). The two controls
         * answer different questions and must not collide. The lockout is per account, durable and
         * precise: it fires at the fifth failure and reports {@code ACCOUNT_LOCKED}, which tells a
         * real customer what happened. The limiter is a coarser backstop covering what the lockout
         * cannot — attempts spread across many accounts, and attempts against addresses that do not
         * exist. If the limiter fired first, every locked-out customer would receive the generic
         * rate-limit error and the specific, actionable one would be unreachable.
         */
        public static Policy defaults() {
            return new Policy(
                    10, 20, Duration.ofMinutes(15),
                    10, Duration.ofMinutes(15),
                    5, Duration.ofMinutes(15),
                    3, 10, Duration.ofMinutes(15),
                    5, Duration.ofMinutes(15),
                    10, Duration.ofHours(1));
        }
    }

    private static final String LOGIN_IDENTIFIER = "rl:login:id:";
    private static final String LOGIN_IP = "rl:login:ip:";
    private static final String ADMIN_LOGIN_IDENTIFIER = "rl:adminlogin:id:";
    private static final String ADMIN_LOGIN_IP = "rl:adminlogin:ip:";
    private static final String OTP_VERIFY = "rl:otp:verify:";
    private static final String OTP_RESEND = "rl:otp:resend:";
    private static final String RESET_IDENTIFIER = "rl:reset:id:";
    private static final String RESET_IP = "rl:reset:ip:";
    private static final String PASSWORD_CHANGE = "rl:pwdchange:id:";
    private static final String REGISTER_IP = "rl:register:ip:";

    private final RateLimiter rateLimiter;
    private final OpaqueTokenHasher keyHasher;
    private final Policy policy;

    public AbuseGuard(RateLimiter rateLimiter, OpaqueTokenHasher keyHasher, Policy policy) {
        this.rateLimiter = rateLimiter;
        this.keyHasher = keyHasher;
        this.policy = policy;
    }

    /**
     * Consumes one login attempt against both the account and the source address.
     *
     * <p>Called for every login request, before the password is checked. A successful login then
     * clears the account counter through {@link #loginSucceeded(String)}.
     */
    public void checkLogin(String identifier, String ipAddress) {
        consume(LOGIN_IDENTIFIER + hash(normalise(identifier)), policy.loginAttemptsPerIdentifier(),
                policy.loginWindow(), "Too many sign-in attempts for this account. Please wait and try again.");
        consumeIfKnown(LOGIN_IP + ipAddress, policy.loginAttemptsPerIp(), policy.loginWindow(),
                "Too many sign-in attempts from this network. Please wait and try again.");
    }

    /**
     * Limits administrator sign-in attempts, with its own counter namespace.
     *
     * <p>A separate key from {@link #checkLogin} is deliberate. Administrator accounts are far fewer
     * and far more valuable than customer accounts, so an attack on them must not be able to consume
     * the budget that a shared NAT of customers relies on, and a flood against customers must not
     * throttle the seller out of their own dashboard. The thresholds are the same; only the counter
     * is distinct.
     */
    public void checkAdminLogin(String email, String ipAddress) {
        consume(ADMIN_LOGIN_IDENTIFIER + hash(normalise(email)), policy.loginAttemptsPerIdentifier(),
                policy.loginWindow(), "Too many sign-in attempts for this account. Please wait and try again.");
        consumeIfKnown(ADMIN_LOGIN_IP + ipAddress, policy.loginAttemptsPerIp(), policy.loginWindow(),
                "Too many sign-in attempts from this network. Please wait and try again.");
    }

    /** Clears the administrator account counter after a successful sign-in. */
    public void adminLoginSucceeded(String email) {
        rateLimiter.reset(ADMIN_LOGIN_IDENTIFIER + hash(normalise(email)));
    }

    /**
     * Clears the account counter after a successful sign-in.
     *
     * <p>Only the account key is cleared, not the address key. Clearing the address counter would let
     * anyone who knows one valid password reset the network-wide limit at will, which is the counter
     * that stops an attacker spraying many accounts from one host.
     */
    public void loginSucceeded(String identifier) {
        rateLimiter.reset(LOGIN_IDENTIFIER + hash(normalise(identifier)));
    }

    /** Limits how often a customer may submit verification codes, across challenge renewals. */
    public void checkOtpVerification(String customerId) {
        consume(OTP_VERIFY + hash(customerId), policy.otpVerificationsPerCustomer(), policy.otpWindow(),
                "Too many verification attempts. Please wait before trying again.");
    }

    /** Limits resend requests per customer, on top of the per-challenge limits in the domain. */
    public void checkOtpResend(String customerId) {
        consume(OTP_RESEND + hash(customerId), policy.otpResendsPerCustomer(), policy.otpResendWindow(),
                "Too many verification codes requested. Please wait before requesting another.");
    }

    /** Limits password-reset requests per address and per source address. */
    public void checkPasswordReset(String identifier, String ipAddress) {
        consume(RESET_IDENTIFIER + hash(normalise(identifier)), policy.passwordResetsPerIdentifier(),
                policy.passwordResetWindow(),
                "Too many password reset requests. Please wait before requesting another.");
        consumeIfKnown(RESET_IP + ipAddress, policy.passwordResetsPerIp(), policy.passwordResetWindow(),
                "Too many password reset requests from this network. Please wait and try again.");
    }

    /**
     * Limits how often a signed-in customer may attempt a password change.
     *
     * <p>The current-password field is a second place an attacker can guess a password, so it needs
     * its own limiter rather than relying on the sign-in one, which is only consumed at login. A
     * per-customer key is the right scope: the caller is already authenticated, so the account is
     * known and the counter cannot be evaded by rotating addresses.
     */
    public void checkPasswordChange(String customerId) {
        consume(PASSWORD_CHANGE + hash(customerId), policy.passwordChangesPerCustomer(),
                policy.passwordChangeWindow(),
                "Too many password change attempts. Please wait and try again.");
    }

    /** Limits account creation per source address, so the signup form cannot be used as a mail relay. */
    public void checkRegistration(String ipAddress) {
        consumeIfKnown(REGISTER_IP + ipAddress, policy.registrationsPerIp(), policy.registrationWindow(),
                "Too many accounts created from this network. Please try again later.");
    }

    private void consume(String key, int limit, Duration window, String message) {
        RateLimiter.Decision decision = rateLimiter.tryConsume(key, limit, window.toSeconds());
        if (!decision.allowed()) {
            throw new RateLimitedException(message, decision.retryAfterSeconds());
        }
    }

    /**
     * Applies a limit only when a source address is available.
     *
     * <p>Some callers legitimately have no address — a scheduled job, a message consumer, a test. Those
     * paths must not be blocked by a limit they cannot satisfy, so the check is skipped rather than
     * applied against a shared placeholder key, which would make every such caller throttle every
     * other one.
     */
    private void consumeIfKnown(String key, int limit, Duration window, String message) {
        if (key.endsWith(":") || key.endsWith(":null")) {
            return;
        }
        consume(key, limit, window, message);
    }

    private String normalise(String identifier) {
        return identifier == null ? "" : identifier.trim().toLowerCase(java.util.Locale.ROOT);
    }

    private String hash(String value) {
        return keyHasher.hash(value);
    }
}
