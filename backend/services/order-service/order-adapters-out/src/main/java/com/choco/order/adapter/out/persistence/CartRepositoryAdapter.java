package com.choco.order.adapter.out.persistence;

import com.choco.order.domain.cart.Cart;
import com.choco.order.domain.port.CartRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * JPA implementation of {@link CartRepository}.
 *
 * <p>One cart per customer is a database constraint (a unique index on {@code customer_id}) as well as
 * an application assumption, so two concurrent "add to cart" calls for a new customer cannot create
 * two carts even if both find none.
 */
@Repository
class CartRepositoryAdapter implements CartRepository {

    private final SpringDataCartRepository delegate;

    CartRepositoryAdapter(SpringDataCartRepository delegate) {
        this.delegate = delegate;
    }

    @Override
    public Cart save(Cart cart) {
        CartJpaEntity entity = OrderPersistenceMapper.toEntity(cart);
        delegate.findVersionById(cart.id()).ifPresent(entity::setVersion);
        delegate.save(entity);
        return cart;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Cart> findByCustomerId(UUID customerId) {
        return delegate.findByCustomerId(customerId).map(OrderPersistenceMapper::toDomain);
    }
}
