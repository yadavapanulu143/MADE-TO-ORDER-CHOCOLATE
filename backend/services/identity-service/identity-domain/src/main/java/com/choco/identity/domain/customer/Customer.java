package com.choco.identity.domain.customer;

import com.choco.core.domain.AggregateRoot;
import com.choco.core.domain.EmailAddress;
import com.choco.core.domain.PhoneNumber;
import com.choco.core.error.BusinessRuleException;
import com.choco.core.error.ErrorCode;
import com.choco.identity.domain.event.CustomerPasswordChanged;
import com.choco.identity.domain.event.CustomerRegistered;
import com.choco.identity.domain.event.CustomerVerified;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

/**
 * A customer account and its verification state.
 *
 * <p>The rule that matters here is that a customer is usable only when both the email and the mobile
 * channel are verified. That rule lives in this aggregate rather than in a controller, so no API
 * path can accidentally bypass it.
 *
 * <p>The password hash is held as an opaque string. This class never hashes, never compares and
 * never interprets it; hashing is a port the application layer drives.
 */
public class Customer extends AggregateRoot {

    /** Failed logins tolerated before the account is temporarily locked. */
    public static final int MAX_FAILED_LOGIN_ATTEMPTS = 5;
    private static final int LOCK_DURATION_MINUTES = 15;

    private final UUID id;
    private ContactDetails contact;
    private String passwordHash;
    private final Set<VerificationChannel> verifiedChannels;
    private final Instant registeredAt;
    private Instant updatedAt;
    private int failedLoginAttempts;
    private Instant lockedUntil;
    private boolean active;

    private Customer(UUID id, ContactDetails contact, String passwordHash, Instant now) {
        this.id = id;
        this.contact = contact;
        this.passwordHash = passwordHash;
        this.verifiedChannels = EnumSet.noneOf(VerificationChannel.class);
        this.registeredAt = now;
        this.updatedAt = now;
        this.failedLoginAttempts = 0;
        this.active = true;
    }

    /** Rehydrates an existing customer from persistence without raising events. */
    public static Customer rehydrate(UUID id, ContactDetails contact, String passwordHash,
                                     Set<VerificationChannel> verifiedChannels, Instant registeredAt,
                                     Instant updatedAt, int failedLoginAttempts, Instant lockedUntil,
                                     boolean active) {
        Customer customer = new Customer(id, contact, passwordHash, registeredAt);
        customer.verifiedChannels.addAll(verifiedChannels);
        customer.updatedAt = updatedAt;
        customer.failedLoginAttempts = failedLoginAttempts;
        customer.lockedUntil = lockedUntil;
        customer.active = active;
        return customer;
    }

    public static Customer register(UUID id, ContactDetails contact, String passwordHash, Instant now) {
        Customer customer = new Customer(id, contact, passwordHash, now);
        customer.registerEvent(new CustomerRegistered(id, now, id.toString(), contact.email().value(),
                contact.mobile().value(), contact.fullName()));
        return customer;
    }

    /**
     * Marks one channel verified.
     *
     * <p>Idempotent by design: a customer may tap a verification link twice, and that must not
     * raise a second {@code CustomerVerified} event that would trigger a duplicate welcome message.
     */
    public void verify(VerificationChannel channel, Instant now) {
        boolean newlyVerified = verifiedChannels.add(channel);
        this.updatedAt = now;
        if (newlyVerified) {
            registerEvent(new CustomerVerified(id, now, id.toString(), channel.name(), fullyVerified()));
        }
    }

    public void changePassword(String newPasswordHash, Instant now) {
        if (newPasswordHash == null || newPasswordHash.isBlank()) {
            throw new IllegalArgumentException("Password hash is required");
        }
        this.passwordHash = newPasswordHash;
        this.updatedAt = now;
        // A password change ends any lockout: the legitimate owner has proved control of the account.
        this.failedLoginAttempts = 0;
        this.lockedUntil = null;
        registerEvent(new CustomerPasswordChanged(id, now, id.toString()));
    }

    /**
     * Changes the account's full name.
     *
     * <p>Only the name. The email and mobile are the account's identity and each carries its own
     * verified flag, so they are changed through {@link #changeEmail} and {@link #changeMobile},
     * which can revoke that verification.
     */
    public void changeFullName(String fullName, Instant now) {
        this.contact = new ContactDetails(contact.email(), contact.mobile(), fullName);
        this.updatedAt = now;
    }

    /**
     * Changes the email address and clears the email verification.
     *
     * <p>Clearing the flag is the whole point: the new address has not been proven to belong to this
     * customer, and leaving the old {@code EMAIL} verification in place would mark an unverified
     * address as verified. The account therefore stops being {@link #fullyVerified()} until the new
     * address is confirmed.
     */
    public void changeEmail(EmailAddress email, Instant now) {
        this.contact = new ContactDetails(email, contact.mobile(), contact.fullName());
        this.verifiedChannels.remove(VerificationChannel.EMAIL);
        this.updatedAt = now;
    }

    /** Changes the mobile number and clears the mobile verification; see {@link #changeEmail}. */
    public void changeMobile(PhoneNumber mobile, Instant now) {
        this.contact = new ContactDetails(contact.email(), mobile, contact.fullName());
        this.verifiedChannels.remove(VerificationChannel.MOBILE);
        this.updatedAt = now;
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

    public void deactivate(Instant now) {
        this.active = false;
        this.updatedAt = now;
    }

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

    public boolean isVerified(VerificationChannel channel) {
        return verifiedChannels.contains(channel);
    }

    public boolean fullyVerified() {
        return verifiedChannels.containsAll(EnumSet.allOf(VerificationChannel.class));
    }

    public UUID id() {
        return id;
    }

    public ContactDetails contact() {
        return contact;
    }

    public String passwordHash() {
        return passwordHash;
    }

    public Set<VerificationChannel> verifiedChannels() {
        return Set.copyOf(verifiedChannels);
    }

    public Instant registeredAt() {
        return registeredAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    public int failedLoginAttempts() {
        return failedLoginAttempts;
    }

    public Instant lockedUntil() {
        return lockedUntil;
    }

    public boolean isActive() {
        return active;
    }
}
