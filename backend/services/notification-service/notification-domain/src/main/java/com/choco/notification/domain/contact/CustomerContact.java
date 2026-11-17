package com.choco.notification.domain.contact;

import java.time.Instant;
import java.util.UUID;

/**
 * A local copy of the little a notification needs to know about a customer: how to reach them.
 *
 * <p>The notification service is a consumer, not the owner of a customer's identity. Rather than call
 * the identity service on every send — which would put a network hop, and a new failure mode, on the
 * delivery path — it keeps this projection, fed by the identity service's own events. The customer id
 * is the key; the rest is a snapshot.
 *
 * <p>This is deliberately the <em>only</em> place in this service that holds a customer's address, and
 * it is deliberately narrow: no password material, no verification flags, nothing but what an email or
 * an SMS needs. The producers' events carry a customer id, never an address, so a customer's email and
 * number do not end up scattered across every service's outbox and event stream.
 *
 * <p>It is a projection and is treated as such: an update overwrites it, and a missing entry is a
 * retryable condition (the identity event may simply not have arrived yet) rather than a hard error.
 */
public record CustomerContact(
        UUID customerId,
        String email,
        String mobile,
        String fullName,
        Instant updatedAt) {

    public CustomerContact {
        if (customerId == null) {
            throw new IllegalArgumentException("customerId is required");
        }
        if ((email == null || email.isBlank()) && (mobile == null || mobile.isBlank())) {
            throw new IllegalArgumentException("At least one of email or mobile is required");
        }
    }

    public boolean hasEmail() {
        return email != null && !email.isBlank();
    }

    public boolean hasMobile() {
        return mobile != null && !mobile.isBlank();
    }
}
