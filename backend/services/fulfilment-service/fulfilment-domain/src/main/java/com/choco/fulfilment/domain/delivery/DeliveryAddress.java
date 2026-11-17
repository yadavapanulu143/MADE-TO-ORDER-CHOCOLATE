package com.choco.fulfilment.domain.delivery;

import com.choco.core.domain.PhoneNumber;
import com.choco.core.domain.Pincode;

/**
 * The delivery address this service will deliver to, copied from the order at hand-over.
 *
 * <p>Copied rather than referenced for the same reason the order service copies it from identity: a
 * customer can edit or delete an address after ordering, and the seller still has to deliver to the
 * address that was agreed. The recipient is carried separately from the account holder because
 * chocolates are frequently ordered as gifts.
 *
 * <p>This is the fulfilment service's own value type, not the order service's class. Depending on the
 * order service's Java type would make a rename there a compile break here, and the two services are
 * meant to share only a JSON contract.
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

    public DeliveryAddress {
        recipientName = requireLine(recipientName, "Recipient name");
        if (recipientName.length() < 2) {
            throw new IllegalArgumentException("Recipient name must be at least 2 characters");
        }
        if (mobile == null) {
            throw new IllegalArgumentException("Recipient mobile number is required");
        }
        line1 = requireLine(line1, "Address line 1");
        line2 = optionalLine(line2);
        city = requireLine(city, "City");
        state = requireLine(state, "State");
        if (pincode == null) {
            throw new IllegalArgumentException("PIN code is required");
        }
        deliveryInstructions = optionalLine(deliveryInstructions);
    }

    /** A single-line rendering for the delivery sheet and invoices. */
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

    private static String optionalLine(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim().replaceAll("\\s+", " ");
        return trimmed.length() > MAX_LINE ? trimmed.substring(0, MAX_LINE) : trimmed;
    }
}
