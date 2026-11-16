package com.choco.identity.domain.admin;

import com.choco.core.domain.AggregateRoot;
import com.choco.core.domain.EmailAddress;
import com.choco.core.error.BusinessRuleException;
import com.choco.core.error.ErrorCode;
import java.time.Instant;
import java.util.UUID;

/**
 * An administrator account.
 *
 * <p>Deliberately a separate aggregate from {@code Customer}, not a customer with a role column.
 * The two have different identities (an admin has no mobile number and never goes through OTP
 * verification), different lifecycles (an admin is provisioned, never self-registered) and
 * different failure modes. Folding them into one table would mean a bug in customer self-service
 * could edit an administrator row, and the column that distinguishes the two would be the only
 * thing standing between a customer and the admin dashboard.
 *
 * <p>As with {@code Customer}, the password hash is an opaque string this class never interprets.
 */
public class AdminAccount extends AggregateRoot {

    /** Failed logins tolerated before the account is temporarily locked. */
    public static final int MAX_FAILED_LOGIN_ATTEMPTS = 5;
    private static final int LOCK_DURATION_MINUTES = 15;

    private final UUID id;
    private String fullName;
    private EmailAddress email;
    private String passwordHash;
    private AdminRole role;
    private boolean active;
    private int failedLoginAttempts;
    private Instant lockedUntil;
    private final Instant createdAt;
    private Instant updatedAt;

    private AdminAccount(UUID id, String fullName, EmailAddress email, String passwordHash,
                         AdminRole role, Instant now) {
        this.id = id;
        this.fullName = fullName;
        this.email = email;
        this.passwordHash = passwordHash;
        this.role = role;
        this.active = true;
        this.createdAt = now;
        this.updatedAt = now;
    }

    /** Rehydrates an existing administrator from persistence without side effects. */
    public static AdminAccount rehydrate(UUID id, String fullName, EmailAddress email, String passwordHash,
                                         AdminRole role, boolean active, int failedLoginAttempts,
                                         Instant lockedUntil, Instant createdAt, Instant updatedAt) {
        AdminAccount account = new AdminAccount(id, fullName, email, passwordHash, role, createdAt);
        account.active = active;
        account.failedLoginAttempts = failedLoginAttempts;
        account.lockedUntil = lockedUntil;
        account.updatedAt = updatedAt;
        return account;
    }

    /** Provisions a new administrator. Only reachable from the bootstrap or an admin-management path. */
    public static AdminAccount provision(UUID id, String fullName, EmailAddress email,
                                         String passwordHash, AdminRole role, Instant now) {
        if (fullName == null || fullName.isBlank()) {
            throw new IllegalArgumentException("Administrator name is required");
        }
        if (passwordHash == null || passwordHash.isBlank()) {
            throw new IllegalArgumentException("Password hash is required");
        }
        return new AdminAccount(id, fullName.trim(), email, passwordHash, role, now);
    }

    /** Records a failed login and locks the account once the threshold is reached. */
    public void recordFailedLogin(Instant now) {
        this.failedLoginAttempts++;
        if (failedLoginAttempts >= MAX_FAILED_LOGIN_ATTEMPTS) {
            this.lockedUntil = now.plusSeconds(LOCK_DURATION_MINUTES * 60L);
        }
        this.updatedAt = now;
    }

    public void recordSuccessfulLogin(Instant now) {
        this.failedLoginAttempts = 0;
        this.lockedUntil = null;
        this.updatedAt = now;
    }

    public void changePassword(String newPasswordHash, Instant now) {
        if (newPasswordHash == null || newPasswordHash.isBlank()) {
            throw new IllegalArgumentException("Password hash is required");
        }
        this.passwordHash = newPasswordHash;
        this.updatedAt = now;
        this.failedLoginAttempts = 0;
        this.lockedUntil = null;
    }

    /** Changes the display name. The email is the account's identity and is not self-service. */
    public void changeFullName(String newFullName, Instant now) {
        if (newFullName == null || newFullName.isBlank()) {
            throw new IllegalArgumentException("Administrator name is required");
        }
        this.fullName = newFullName.trim();
        this.updatedAt = now;
    }

    /** Promotes or demotes the account. Only a super-admin may drive this, enforced above. */
    public void changeRole(AdminRole newRole, Instant now) {
        this.role = newRole;
        this.updatedAt = now;
    }

    public void deactivate(Instant now) {
        this.active = false;
        this.updatedAt = now;
    }

    public void activate(Instant now) {
        this.active = true;
        this.updatedAt = now;
    }

    /** Refuses a disabled or locked account. Called only after the password has been checked. */
    public void assertUsable(Instant now) {
        if (!active) {
            throw new BusinessRuleException(ErrorCode.ACCOUNT_LOCKED, "This account is not active");
        }
        if (isLocked(now)) {
            throw new BusinessRuleException(ErrorCode.ACCOUNT_LOCKED,
                    "Account is temporarily locked after too many failed attempts. Try again later.");
        }
    }

    public boolean isLocked(Instant now) {
        return lockedUntil != null && now.isBefore(lockedUntil);
    }

    public UUID id() {
        return id;
    }

    public String fullName() {
        return fullName;
    }

    public EmailAddress email() {
        return email;
    }

    public String passwordHash() {
        return passwordHash;
    }

    public AdminRole role() {
        return role;
    }

    public boolean isActive() {
        return active;
    }

    public int failedLoginAttempts() {
        return failedLoginAttempts;
    }

    public Instant lockedUntil() {
        return lockedUntil;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }
}
