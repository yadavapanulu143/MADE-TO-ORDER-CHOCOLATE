package com.choco.catalog.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Persistence model for the single website-settings row.
 *
 * <p>Policy text is stored in {@code TEXT} columns rather than a bounded {@code VARCHAR}, because the
 * seller writes these and the application must not silently truncate a legal policy. The length is
 * bounded in the domain instead, where exceeding it is a clear validation error rather than data loss.
 */
@Entity
@Table(name = "website_settings")
public class WebsiteSettingsJpaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "seller_name", length = 150)
    private String sellerName;

    @Column(name = "contact_email", length = 254)
    private String contactEmail;

    @Column(name = "contact_phone", length = 20)
    private String contactPhone;

    @Column(name = "contact_address", columnDefinition = "text")
    private String contactAddress;

    @Column(name = "privacy_policy", columnDefinition = "text")
    private String privacyPolicy;

    @Column(name = "terms_and_conditions", columnDefinition = "text")
    private String termsAndConditions;

    @Column(name = "shipping_policy", columnDefinition = "text")
    private String shippingPolicy;

    @Column(name = "refund_policy", columnDefinition = "text")
    private String refundPolicy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected WebsiteSettingsJpaEntity() {
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getSellerName() {
        return sellerName;
    }

    public void setSellerName(String sellerName) {
        this.sellerName = sellerName;
    }

    public String getContactEmail() {
        return contactEmail;
    }

    public void setContactEmail(String contactEmail) {
        this.contactEmail = contactEmail;
    }

    public String getContactPhone() {
        return contactPhone;
    }

    public void setContactPhone(String contactPhone) {
        this.contactPhone = contactPhone;
    }

    public String getContactAddress() {
        return contactAddress;
    }

    public void setContactAddress(String contactAddress) {
        this.contactAddress = contactAddress;
    }

    public String getPrivacyPolicy() {
        return privacyPolicy;
    }

    public void setPrivacyPolicy(String privacyPolicy) {
        this.privacyPolicy = privacyPolicy;
    }

    public String getTermsAndConditions() {
        return termsAndConditions;
    }

    public void setTermsAndConditions(String termsAndConditions) {
        this.termsAndConditions = termsAndConditions;
    }

    public String getShippingPolicy() {
        return shippingPolicy;
    }

    public void setShippingPolicy(String shippingPolicy) {
        this.shippingPolicy = shippingPolicy;
    }

    public String getRefundPolicy() {
        return refundPolicy;
    }

    public void setRefundPolicy(String refundPolicy) {
        this.refundPolicy = refundPolicy;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
