package com.choco.notification.application.dto;

/**
 * Where the seller is reached.
 *
 * <p>Bound from configuration, because the seller is a single known operator of this business and not
 * an account in the identity service. Kept as a record so it is a plain value the use case receives,
 * not a Spring property bag threaded through the code.
 *
 * <p>An administrative notification is skipped, with a loud log, when the relevant channel has no
 * address configured. That is better than failing: a customer's order confirmation must still go out
 * even if the seller forgot to set their own contact details.
 */
public record SellerContact(String email, String mobile) {

    public boolean hasEmail() {
        return email != null && !email.isBlank();
    }

    public boolean hasMobile() {
        return mobile != null && !mobile.isBlank();
    }
}
