package com.choco.order.application.support;

import com.choco.order.domain.cart.Cart;
import com.choco.order.domain.port.CartRepository;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** In-memory cart store. Keeps real behaviour (one cart per customer) rather than recording calls. */
public class InMemoryCartRepository implements CartRepository {

    private final Map<UUID, Cart> byCustomer = new HashMap<>();

    @Override
    public Cart save(Cart cart) {
        byCustomer.put(cart.customerId(), cart);
        return cart;
    }

    @Override
    public Optional<Cart> findByCustomerId(UUID customerId) {
        return Optional.ofNullable(byCustomer.get(customerId));
    }
}
