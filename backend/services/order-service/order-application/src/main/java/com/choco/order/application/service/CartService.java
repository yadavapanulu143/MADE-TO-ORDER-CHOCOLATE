package com.choco.order.application.service;

import com.choco.core.error.BusinessRuleException;
import com.choco.core.error.ErrorCode;
import com.choco.core.error.NotFoundException;
import com.choco.core.port.Clock;
import com.choco.core.port.IdGenerator;
import com.choco.core.port.TransactionRunner;
import com.choco.order.application.command.AddToCartCommand;
import com.choco.order.application.command.UpdateCartLineCommand;
import com.choco.order.application.dto.CartView;
import com.choco.order.application.port.CatalogPort;
import com.choco.order.domain.cart.Cart;
import com.choco.order.domain.port.CartRepository;
import java.util.UUID;

/**
 * Use cases for the customer's cart.
 *
 * <p>Every method takes the customer id from the caller's token, never from the request, and loads the
 * cart by that id. There is therefore no cart id in any signature that a customer could swap for
 * another's — the ownership check is the lookup itself.
 *
 * <p>Adding a line reads the catalogue first. The cart stores the name and price it read so the
 * storefront can render without a second call, but that stored price is a display value only; checkout
 * re-reads the catalogue and prices the order from there. A stale cart price can never become a stale
 * order price.
 */
public class CartService {

    private final CartRepository cartRepository;
    private final CatalogPort catalogPort;
    private final Clock clock;
    private final IdGenerator idGenerator;
    private final TransactionRunner transactions;

    public CartService(CartRepository cartRepository, CatalogPort catalogPort, Clock clock,
                       IdGenerator idGenerator, TransactionRunner transactions) {
        this.cartRepository = cartRepository;
        this.catalogPort = catalogPort;
        this.clock = clock;
        this.idGenerator = idGenerator;
        this.transactions = transactions;
    }

    public CartView view(UUID customerId) {
        return OrderViewMapper.toView(loadOrCreate(customerId));
    }

    public CartView addLine(UUID customerId, AddToCartCommand command) {
        return transactions.execute(() -> {
            CatalogPort.VariantSnapshot variant = requirePurchasableVariant(command.variantId());
            Cart cart = loadOrCreate(customerId);
            cart.addLine(variant.productId(), variant.variantId(), variant.productName(),
                    variant.variantLabel(), variant.unitPrice(), command.quantity(), clock.now());
            return OrderViewMapper.toView(cartRepository.save(cart));
        });
    }

    public CartView updateLine(UUID customerId, UpdateCartLineCommand command) {
        return transactions.execute(() -> {
            Cart cart = requireCart(customerId);
            cart.setQuantity(command.variantId(), command.quantity(), clock.now());
            return OrderViewMapper.toView(cartRepository.save(cart));
        });
    }

    public CartView removeLine(UUID customerId, UUID variantId) {
        return transactions.execute(() -> {
            Cart cart = requireCart(customerId);
            cart.removeLine(variantId, clock.now());
            return OrderViewMapper.toView(cartRepository.save(cart));
        });
    }

    public CartView clear(UUID customerId) {
        return transactions.execute(() -> {
            Cart cart = requireCart(customerId);
            cart.clear(clock.now());
            return OrderViewMapper.toView(cartRepository.save(cart));
        });
    }

    /** Loads the customer's cart, creating an empty one on first use. */
    Cart loadOrCreate(UUID customerId) {
        return cartRepository.findByCustomerId(customerId)
                .orElseGet(() -> cartRepository.save(Cart.empty(idGenerator.nextId(), customerId, clock.now())));
    }

    private Cart requireCart(UUID customerId) {
        return cartRepository.findByCustomerId(customerId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.NOT_FOUND, "Your cart is empty"));
    }

    /**
     * Resolves a variant that can actually be bought right now.
     *
     * <p>An unknown variant and a withdrawn one are reported differently: the first is a bad id, the
     * second is a product the seller has taken off sale. Both are refused, but the message tells the
     * customer which situation they are in.
     */
    CatalogPort.VariantSnapshot requirePurchasableVariant(UUID variantId) {
        if (variantId == null) {
            throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED, "A variant is required");
        }
        CatalogPort.VariantSnapshot variant = catalogPort.findVariant(variantId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.NOT_FOUND, "That product is not available"));
        if (!variant.purchasable()) {
            throw new BusinessRuleException(ErrorCode.PRODUCT_UNAVAILABLE,
                    "That product is not currently available");
        }
        return variant;
    }
}
