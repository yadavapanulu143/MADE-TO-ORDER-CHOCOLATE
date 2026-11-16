package com.choco.identity.domain.address;

import com.choco.core.domain.PhoneNumber;
import com.choco.core.domain.Pincode;
import com.choco.core.error.BusinessRuleException;
import com.choco.core.error.ErrorCode;
import java.time.Instant;
import java.util.UUID;

/**
 * A delivery address belonging to one customer.
 *
 * <p>An aggregate of its own rather than a collection inside {@code Customer}. Addresses change on a
 * different rhythm from the account — added at checkout, edited, one of them chosen as the default —
 * and loading the whole account to add an address would make every address write contend on the same
 * row as login counters and verification state. The ownership link is the immutable
 * {@link #customerId}, which is never taken from a request body: it comes from the authenticated
 * caller, so a customer cannot file an address against another account.
 *
 * <p>The recipient is not necessarily the account holder. Chocolates are frequently sent as gifts, so
 * a recipient name and mobile are carried per address rather than read from the customer.
 */
public class Address {

    /** Upper bound on saved addresses, so one account cannot fill the table. */
    public static final int MAX_PER_CUSTOMER = 10;

    private static final int MAX_LINE = 200;
    private static final int MAX_INSTRUCTIONS = 500;

    private final UUID id;
    private final UUID customerId;
    private String recipientName;
    private PhoneNumber mobile;
    private String line1;
    private String line2;
    private String city;
    private String state;
    private Pincode pincode;
    private String deliveryInstructions;
    private boolean isDefault;
    private final Instant createdAt;
    private Instant updatedAt;

    private Address(UUID id, UUID customerId, String recipientName, PhoneNumber mobile, String line1,
                    String line2, String city, String state, Pincode pincode, String deliveryInstructions,
                    boolean isDefault, Instant createdAt) {
        this.id = id;
        this.customerId = customerId;
        this.recipientName = requireName(recipientName);
        this.mobile = require(mobile, "Recipient mobile number is required");
        this.line1 = requireLine(line1, "Address line 1");
        this.line2 = optionalLine(line2, "Address line 2");
        this.city = requireLine(city, "City");
        this.state = requireLine(state, "State");
        this.pincode = require(pincode, "PIN code is required");
        this.deliveryInstructions = optionalLine(deliveryInstructions, "Delivery instructions");
        this.isDefault = isDefault;
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
    }

    public static Address create(UUID id, UUID customerId, String recipientName, PhoneNumber mobile,
                                 String line1, String line2, String city, String state, Pincode pincode,
                                 String deliveryInstructions, boolean isDefault, Instant now) {
        if (customerId == null) {
            throw new IllegalArgumentException("Customer id is required");
        }
        return new Address(id, customerId, recipientName, mobile, line1, line2, city, state, pincode,
                deliveryInstructions, isDefault, now);
    }

    /** Rehydrates from persistence without re-running creation rules on already-valid stored data. */
    public static Address rehydrate(UUID id, UUID customerId, String recipientName, PhoneNumber mobile,
                                    String line1, String line2, String city, String state, Pincode pincode,
                                    String deliveryInstructions, boolean isDefault, Instant createdAt,
                                    Instant updatedAt) {
        Address address = new Address(id, customerId, recipientName, mobile, line1, line2, city, state,
                pincode, deliveryInstructions, isDefault, createdAt);
        address.updatedAt = updatedAt;
        return address;
    }

    /**
     * Applies an edit.
     *
     * <p>Every field is validated into a local before any of them is assigned. Assigning as we go
     * would leave the aggregate half-updated when a later field is rejected — the caller sees an
     * exception, but the in-memory object has already changed, and if it were then saved the stored
     * address would be a mix of old and new values.
     *
     * <p>The owning customer cannot be changed: {@code customerId} is final and has no setter, so no
     * update path can move an address to another account.
     */
    public void update(String recipientName, PhoneNumber mobile, String line1, String line2, String city,
                       String state, Pincode pincode, String deliveryInstructions, Instant now) {
        String newRecipientName = requireName(recipientName);
        PhoneNumber newMobile = require(mobile, "Recipient mobile number is required");
        String newLine1 = requireLine(line1, "Address line 1");
        String newLine2 = optionalLine(line2, "Address line 2");
        String newCity = requireLine(city, "City");
        String newState = requireLine(state, "State");
        Pincode newPincode = require(pincode, "PIN code is required");
        String newInstructions = optionalLine(deliveryInstructions, "Delivery instructions");

        this.recipientName = newRecipientName;
        this.mobile = newMobile;
        this.line1 = newLine1;
        this.line2 = newLine2;
        this.city = newCity;
        this.state = newState;
        this.pincode = newPincode;
        this.deliveryInstructions = newInstructions;
        this.updatedAt = now;
    }

    public void markDefault() {
        this.isDefault = true;
    }

    /** Clears the default flag. Called when another address becomes the default. */
    public void clearDefault() {
        this.isDefault = false;
    }

    /** Guards every operation so one customer can never touch another's address. */
    public void assertOwnedBy(UUID callerCustomerId) {
        if (callerCustomerId == null || !customerId.equals(callerCustomerId)) {
            throw new BusinessRuleException(ErrorCode.FORBIDDEN,
                    "You are not allowed to access this address");
        }
    }

    private static String requireName(String value) {
        String trimmed = requireLine(value, "Recipient name");
        if (trimmed.length() < 2) {
            throw new IllegalArgumentException("Recipient name must be at least 2 characters");
        }
        return trimmed;
    }

    private static String requireLine(String value, String field) {
        String trimmed = value == null ? "" : value.trim().replaceAll("\\s+", " ");
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException(field + " is required");
        }
        if (trimmed.length() > MAX_LINE) {
            throw new IllegalArgumentException(field + " must be at most " + MAX_LINE + " characters");
        }
        return trimmed;
    }

    private static String optionalLine(String value, String field) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim().replaceAll("\\s+", " ");
        if (trimmed.length() > MAX_INSTRUCTIONS) {
            throw new IllegalArgumentException(field + " must be at most " + MAX_INSTRUCTIONS + " characters");
        }
        return trimmed;
    }

    private static <T> T require(T value, String message) {
        if (value == null) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }

    public UUID id() {
        return id;
    }

    public UUID customerId() {
        return customerId;
    }

    public String recipientName() {
        return recipientName;
    }

    public PhoneNumber mobile() {
        return mobile;
    }

    public String line1() {
        return line1;
    }

    public String line2() {
        return line2;
    }

    public String city() {
        return city;
    }

    public String state() {
        return state;
    }

    public Pincode pincode() {
        return pincode;
    }

    public String deliveryInstructions() {
        return deliveryInstructions;
    }

    public boolean isDefault() {
        return isDefault;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }
}
