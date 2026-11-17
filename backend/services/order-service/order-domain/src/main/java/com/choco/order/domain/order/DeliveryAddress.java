package com.choco.order.domain.order;

import com.choco.core.domain.PhoneNumber;
import com.choco.core.domain.Pincode;

/**
 * The delivery address as captured at checkout, frozen onto the order.
 *
 * <p>Copied rather than referenced. A customer can edit or delete an address after ordering, and the
 * seller still has to deliver to the address that was agreed. Keeping the identity service's address
 * id would mean a later edit silently rewrote where a past order was sent, which is exactly the fact
 * a dispute turns on.
 *
 * <p>The recipient is carried separately from the account holder because chocolates are frequently
 * ordered as gifts.
 */
public record DeliveryAddress(
        String recipientName,
        PhoneNumber mobile,
        String line1,
        String line2,
        String city,
        String state,
        Pincode pincode,
        String deliveryInstructions) {

    private static final int MAX_LINE = 200;
    private static final int MAX_INSTRUCTIONS = 500;

    public DeliveryAddress {
        recipientName = requireLine(recipientName, "Recipient name");
        if (recipientName.length() < 2) {
            throw new IllegalArgumentException("Recipient name must be at least 2 characters");
        }
        if (mobile == null) {
            throw new IllegalArgumentException("Recipient mobile number is required");
        }
        line1 = requireLine(line1, "Address line 1");
        line2 = optionalLine(line2, "Address line 2");
        city = requireLine(city, "City");
        state = requireLine(state, "State");
        if (pincode == null) {
            throw new IllegalArgumentException("PIN code is required");
        }
        deliveryInstructions = optionalLine(deliveryInstructions, "Delivery instructions");
    }

    /** A single-line rendering for invoices and exports. */
    public String formatted() {
        StringBuilder builder = new StringBuilder(recipientName).append(", ").append(line1);
        if (line2 != null) {
            builder.append(", ").append(line2);
        }
        return builder.append(", ").append(city).append(", ").append(state)
                .append(" ").append(pincode.value()).append(", ").append(mobile.withCountryCode()).toString();
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
}
