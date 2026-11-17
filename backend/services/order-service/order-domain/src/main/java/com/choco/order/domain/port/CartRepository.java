package com.choco.order.domain.port;

import com.choco.order.domain.cart.Cart;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence port for carts.
 *
 * <p>Lookup is by customer, because a customer has one active cart and never addresses a cart by an id
 * they supply. That keeps the ownership check inherent in the query rather than a comparison someone
 * could forget.
 */
public interface CartRepository {

    Cart save(Cart cart);

    Optional<Cart> findByCustomerId(UUID customerId);
}
