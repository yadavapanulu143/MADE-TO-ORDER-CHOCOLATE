package com.choco.catalog.domain.settings;

import com.choco.core.domain.AggregateRoot;
import java.time.Instant;
import java.util.UUID;

/**
 * The seller's public website content: who they are, how to reach them, and the policies the storefront
 * must display.
 *
 * <p>Held as a single row rather than a key/value table. The set of settings is small and known, and
 * named columns give each one a type and a length, so a policy cannot be saved as an unbounded blob and
 * the storefront reads a typed value instead of parsing a string. A key/value table would push every
 * validation into the application and make "which settings exist?" a runtime question.
 *
 * <p>The legal policies are stored verbatim. The application does not generate or summarise them: the
 * seller is responsible for their wording, and the platform's job is to publish what the seller
 * supplies. Defaults are left empty so an unfilled policy is visibly absent rather than silently
 * replaced with placeholder text a customer might read as a real commitment.
 */
public class WebsiteSettings extends AggregateRoot {

    public static final int MAX_SELLER_NAME = 150;
    public static final int MAX_CONTACT_EMAIL = 254;
    public static final int MAX_CONTACT_PHONE = 20;
    public static final int MAX_TEXT = 20_000;

    private final UUID id;
    private String sellerName;
    private String contactEmail;
    private String contactPhone;
    private String contactAddress;
    private String privacyPolicy;
    private String termsAndConditions;
    private String shippingPolicy;
    private String refundPolicy;
    private Instant updatedAt;

    private WebsiteSettings(UUID id, String sellerName, String contactEmail, String contactPhone,
                            String contactAddress, String privacyPolicy, String termsAndConditions,
                            String shippingPolicy, String refundPolicy, Instant updatedAt) {
        this.id = id;
        this.sellerName = optional(sellerName, MAX_SELLER_NAME, "Seller name");
        this.contactEmail = optional(contactEmail, MAX_CONTACT_EMAIL, "Contact email");
        this.contactPhone = optional(contactPhone, MAX_CONTACT_PHONE, "Contact phone");
        this.contactAddress = optional(contactAddress, MAX_TEXT, "Contact address");
        this.privacyPolicy = optional(privacyPolicy, MAX_TEXT, "Privacy policy");
        this.termsAndConditions = optional(termsAndConditions, MAX_TEXT, "Terms and conditions");
        this.shippingPolicy = optional(shippingPolicy, MAX_TEXT, "Shipping policy");
        this.refundPolicy = optional(refundPolicy, MAX_TEXT, "Refund policy");
        this.updatedAt = updatedAt;
    }

    /** The settings row for a fresh installation: every field empty, awaiting the seller's input. */
    public static WebsiteSettings empty(UUID id, Instant now) {
        return new WebsiteSettings(id, null, null, null, null, null, null, null, null, now);
    }

    public static WebsiteSettings rehydrate(UUID id, String sellerName, String contactEmail,
                                            String contactPhone, String contactAddress, String privacyPolicy,
                                            String termsAndConditions, String shippingPolicy,
                                            String refundPolicy, Instant updatedAt) {
        return new WebsiteSettings(id, sellerName, contactEmail, contactPhone, contactAddress,
                privacyPolicy, termsAndConditions, shippingPolicy, refundPolicy, updatedAt);
    }

    /** Replaces the whole content. Every field is validated before any is assigned. */
    public void update(String sellerName, String contactEmail, String contactPhone, String contactAddress,
                       String privacyPolicy, String termsAndConditions, String shippingPolicy,
                       String refundPolicy, Instant now) {
        String newSellerName = optional(sellerName, MAX_SELLER_NAME, "Seller name");
        String newEmail = optional(contactEmail, MAX_CONTACT_EMAIL, "Contact email");
        String newPhone = optional(contactPhone, MAX_CONTACT_PHONE, "Contact phone");
        String newAddress = optional(contactAddress, MAX_TEXT, "Contact address");
        String newPrivacy = optional(privacyPolicy, MAX_TEXT, "Privacy policy");
        String newTerms = optional(termsAndConditions, MAX_TEXT, "Terms and conditions");
        String newShipping = optional(shippingPolicy, MAX_TEXT, "Shipping policy");
        String newRefund = optional(refundPolicy, MAX_TEXT, "Refund policy");

        this.sellerName = newSellerName;
        this.contactEmail = newEmail;
        this.contactPhone = newPhone;
        this.contactAddress = newAddress;
        this.privacyPolicy = newPrivacy;
        this.termsAndConditions = newTerms;
        this.shippingPolicy = newShipping;
        this.refundPolicy = newRefund;
        this.updatedAt = now;
    }

    private static String optional(String value, int maxLength, String field) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.length() > maxLength) {
            throw new IllegalArgumentException(field + " must be at most " + maxLength + " characters");
        }
        return trimmed;
    }

    public UUID id() {
        return id;
    }

    public String sellerName() {
        return sellerName;
    }

    public String contactEmail() {
        return contactEmail;
    }

    public String contactPhone() {
        return contactPhone;
    }

    public String contactAddress() {
        return contactAddress;
    }

    public String privacyPolicy() {
        return privacyPolicy;
    }

    public String termsAndConditions() {
        return termsAndConditions;
    }

    public String shippingPolicy() {
        return shippingPolicy;
    }

    public String refundPolicy() {
        return refundPolicy;
    }

    public Instant updatedAt() {
        return updatedAt;
    }
}
