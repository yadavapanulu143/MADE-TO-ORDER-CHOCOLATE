package com.choco.reporting.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * The customer read model as stored, keyed on the identity service's customer id.
 *
 * <p>There is deliberately no column for a password, a hash, an OTP or a token. The table records who
 * a customer is for reporting purposes, and nothing that could be used to authenticate as them. The
 * email and mobile are masked before any export; the export mapping never emits them raw.
 */
@Entity
@Table(name = "customer_projection")
public class CustomerProjectionJpaEntity {

    @Id
    @Column(name = "customer_id", nullable = false, updatable = false, length = 64)
    private String customerId;

    @Column(name = "full_name", length = 150)
    private String fullName;

    @Column(name = "email", length = 254)
    private String email;

    @Column(name = "mobile", length = 20)
    private String mobile;

    @Column(name = "email_verified", nullable = false)
    private boolean emailVerified;

    @Column(name = "mobile_verified", nullable = false)
    private boolean mobileVerified;

    @Column(name = "registered_at", nullable = false)
    private Instant registeredAt;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    protected CustomerProjectionJpaEntity() {
    }

    public String getCustomerId() { return customerId; }
    public void setCustomerId(String customerId) { this.customerId = customerId; }
    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getMobile() { return mobile; }
    public void setMobile(String mobile) { this.mobile = mobile; }
    public boolean isEmailVerified() { return emailVerified; }
    public void setEmailVerified(boolean emailVerified) { this.emailVerified = emailVerified; }
    public boolean isMobileVerified() { return mobileVerified; }
    public void setMobileVerified(boolean mobileVerified) { this.mobileVerified = mobileVerified; }
    public Instant getRegisteredAt() { return registeredAt; }
    public void setRegisteredAt(Instant registeredAt) { this.registeredAt = registeredAt; }
    public Instant getVerifiedAt() { return verifiedAt; }
    public void setVerifiedAt(Instant verifiedAt) { this.verifiedAt = verifiedAt; }
}
