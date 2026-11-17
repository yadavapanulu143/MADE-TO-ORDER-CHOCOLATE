package com.choco.notification.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * The customer-contact projection.
 *
 * <p>The customer id is the primary key and is <em>not</em> generated here: it is the identity
 * service's id, and this row is a copy keyed by it. That makes the upsert a natural "insert or update
 * by id" rather than a search-and-replace.
 *
 * <p>Only what a message needs is stored. There is no password hash, no verification flag, no address
 * book: this service sends email and SMS, and a wider copy of the customer would be a second place to
 * leak from for no benefit.
 */
@Entity
@Table(name = "customer_contacts")
public class CustomerContactJpaEntity {

    @Id
    @Column(name = "customer_id", nullable = false, updatable = false)
    private UUID customerId;

    @Column(name = "email", length = 254)
    private String email;

    @Column(name = "mobile", length = 20)
    private String mobile;

    @Column(name = "full_name", length = 120)
    private String fullName;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected CustomerContactJpaEntity() {
    }

    public UUID getCustomerId() {
        return customerId;
    }

    public void setCustomerId(UUID customerId) {
        this.customerId = customerId;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getMobile() {
        return mobile;
    }

    public void setMobile(String mobile) {
        this.mobile = mobile;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
