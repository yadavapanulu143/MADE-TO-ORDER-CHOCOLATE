package com.choco.order.domain.order;

import com.choco.core.domain.EmailAddress;
import com.choco.core.domain.PhoneNumber;

/**
 * The customer's contact details as captured when the order was placed.
 *
 * <p>Copied from the verified account rather than referenced. The order record must be able to show
 * the name, email and mobile that the customer had at purchase time even if they later change them,
 * because those are the details an invoice, a delivery and any dispute are about.
 */
public record CustomerSnapshot(String fullName, EmailAddress email, PhoneNumber mobile) {

    public CustomerSnapshot {
        if (fullName == null || fullName.isBlank()) {
            throw new IllegalArgumentException("Customer name is required");
        }
        fullName = fullName.trim().replaceAll("\\s+", " ");
        if (email == null) {
            throw new IllegalArgumentException("Customer email is required");
        }
        if (mobile == null) {
            throw new IllegalArgumentException("Customer mobile number is required");
        }
    }
}
