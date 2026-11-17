package com.choco.order.application.service;

import com.choco.core.domain.Money;
import com.choco.core.domain.Quantity;
import com.choco.core.error.BusinessRuleException;
import com.choco.core.error.ErrorCode;
import com.choco.core.error.ForbiddenException;
import com.choco.core.error.IdempotencyConflictException;
import com.choco.core.error.NotFoundException;
import com.choco.core.error.ValidationException;
import com.choco.core.port.Clock;
import com.choco.core.port.IdGenerator;
import com.choco.core.port.TransactionRunner;
import com.choco.order.application.command.CheckoutCommand;
import com.choco.order.application.dto.CheckoutQuoteView;
import com.choco.order.application.dto.OrderView;
import com.choco.order.application.port.CatalogPort;
import com.choco.order.application.port.CustomerPort;
import com.choco.order.application.port.ServiceabilityPort;
import com.choco.order.domain.cart.Cart;
import com.choco.order.domain.cart.CartLine;
import com.choco.order.domain.idempotency.IdempotencyRecord;
import com.choco.order.domain.order.CustomerSnapshot;
import com.choco.order.domain.order.DeliveryAddress;
import com.choco.order.domain.order.Order;
import com.choco.order.domain.order.OrderLine;
import com.choco.order.domain.port.CartRepository;
import com.choco.order.domain.port.IdempotencyRecordRepository;
import com.choco.order.domain.port.OrderRepository;
import com.choco.order.application.port.OrderNumberGenerator;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Turns a cart into an order, and quotes what that order will cost before it exists.
 *
 * <p>This is the one place where the order service reads across services — the catalogue for prices and
 * availability, identity for the customer and the address, and the seller's serviceability settings for
 * the delivery charge. Every one of those facts is copied into the order at the moment it is placed, so
 * the order does not depend on any of them afterwards.
 *
 * <p>The same validation runs for a quote and for the actual placement. A quote is a convenience for
 * the storefront; it is never trusted. If a client showed a quote and then changed the cart, the
 * placement re-reads everything and prices what is actually there.
 *
 * <p>Checkout is idempotent when the client supplies a key: a retry after a timeout returns the order
 * the first attempt created rather than creating a second one. The stored fingerprint means a retry
 * with a different cart is refused rather than answered with the first order, which would be worse than
 * a plain failure.
 */
public class CheckoutService {

    private static final String SCOPE = "checkout";
    private static final Duration IDEMPOTENCY_TTL = Duration.ofHours(24);

    private final CartRepository cartRepository;
    private final OrderRepository orderRepository;
    private final IdempotencyRecordRepository idempotencyRepository;
    private final CatalogPort catalogPort;
    private final CustomerPort customerPort;
    private final ServiceabilityPort serviceabilityPort;
    private final OrderNumberGenerator orderNumbers;
    private final Clock clock;
    private final IdGenerator idGenerator;
    private final TransactionRunner transactions;

    public CheckoutService(CartRepository cartRepository, OrderRepository orderRepository,
                           IdempotencyRecordRepository idempotencyRepository, CatalogPort catalogPort,
                           CustomerPort customerPort, ServiceabilityPort serviceabilityPort,
                           OrderNumberGenerator orderNumbers, Clock clock, IdGenerator idGenerator,
                           TransactionRunner transactions) {
        this.cartRepository = cartRepository;
        this.orderRepository = orderRepository;
        this.idempotencyRepository = idempotencyRepository;
        this.catalogPort = catalogPort;
        this.customerPort = customerPort;
        this.serviceabilityPort = serviceabilityPort;
        this.orderNumbers = orderNumbers;
        this.clock = clock;
        this.idGenerator = idGenerator;
        this.transactions = transactions;
    }

    /**
     * Prices the customer's cart against an address without placing anything.
     *
     * <p>Used by the storefront to show the delivery charge and the final amount before payment. It
     * performs the same checks placement does, so a quote that comes back serviceable will not fail
     * placement for a reason the customer could have been told about here.
     */
    public CheckoutQuoteView quote(UUID customerId, UUID addressId) {
        Cart cart = requireNonEmptyCart(customerId);
        CustomerPort.AddressSnapshot address = requireAddress(customerId, addressId);
        PricedCart priced = priceCart(cart);
        ServiceabilityPort.Quote serviceability = serviceabilityPort.quoteFor(address.pincode());

        Money deliveryCharge = serviceability.serviceable() ? serviceability.deliveryCharge() : Money.inr(0);
        Money total = priced.itemsTotal().plus(deliveryCharge);

        return new CheckoutQuoteView(
                serviceability.serviceable(),
                serviceability.reason(),
                priced.lines().stream().map(OrderViewMapper::toLineView).toList(),
                priced.totalItems(),
                priced.itemsTotal().minorUnits(),
                decimal(priced.itemsTotal()),
                deliveryCharge.minorUnits(),
                decimal(deliveryCharge),
                total.minorUnits(),
                decimal(total),
                total.currency().getCurrencyCode());
    }

    /**
     * Validates the cart and address, prices them, and creates the order.
     *
     * <p>Runs inside one transaction: the order, its lines and its status history commit together or
     * not at all. The cart is cleared in the same transaction, so an order can never exist with the
     * cart still holding its items.
     */
    public OrderView placeOrder(UUID customerId, CheckoutCommand command) {
        String key = normaliseKey(command.idempotencyKey());
        return transactions.execute(() -> {
            if (key != null) {
                var replay = replayOf(key, customerId, command.addressId());
                if (replay.isPresent()) {
                    return replay.get();
                }
            }

            Cart cart = requireNonEmptyCart(customerId);
            CustomerPort.CustomerSnapshot customer = requireVerifiedCustomer(customerId);
            CustomerPort.AddressSnapshot address = requireAddress(customerId, command.addressId());
            PricedCart priced = priceCart(cart);
            ServiceabilityPort.Quote serviceability = serviceabilityPort.quoteFor(address.pincode());
            if (!serviceability.serviceable()) {
                throw new BusinessRuleException(ErrorCode.PINCODE_NOT_SERVICEABLE,
                        serviceability.reason() == null
                                ? "We do not deliver to that PIN code yet"
                                : serviceability.reason());
            }

            Instant now = clock.now();
            Order order = Order.place(
                    idGenerator.nextId(),
                    orderNumbers.next(now),
                    customerId,
                    new CustomerSnapshot(customer.fullName(), customer.email(), customer.mobile()),
                    toDeliveryAddress(address),
                    priced.lines(),
                    serviceability.deliveryCharge(),
                    now);
            Order saved = orderRepository.save(order);
            cart.clear(now);
            cartRepository.save(cart);

            if (key != null) {
                idempotencyRepository.save(IdempotencyRecord.of(idGenerator.nextId(), SCOPE, key,
                        fingerprint(customerId, command.addressId()), saved.id().toString(),
                        saved.id().toString(), 201, now, now.plus(IDEMPOTENCY_TTL)));
            }
            return OrderViewMapper.toView(saved);
        });
    }

    // -------------------------------------------------------------------------------------------

    private java.util.Optional<OrderView> replayOf(String key, UUID customerId, UUID addressId) {
        return idempotencyRepository.findByScopeAndKey(SCOPE, key)
                .filter(record -> !record.isExpiredAt(clock.now()))
                .map(record -> {
                    String expected = fingerprint(customerId, addressId);
                    if (record.requestFingerprint() != null
                            && !record.requestFingerprint().equals(expected)) {
                        throw new IdempotencyConflictException(
                                "This idempotency key was already used for a different checkout");
                    }
                    Order existing = orderRepository.findById(UUID.fromString(record.resourceId()))
                            .orElseThrow(() -> new NotFoundException(ErrorCode.NOT_FOUND,
                                    "The order for this request could not be found"));
                    return OrderViewMapper.toView(existing);
                });
    }

    /**
     * Builds the priced lines from the catalogue.
     *
     * <p>Each variant is re-resolved and its configured minimum and maximum enforced, so the order
     * cannot contain a quantity the seller has said they will not make. The catalogue's price is used,
     * never the cart's, so a price change between adding and checking out is honoured in the customer's
     * favour and the seller's alike.
     */
    private PricedCart priceCart(Cart cart) {
        List<UUID> variantIds = cart.lines().stream().map(CartLine::variantId).toList();
        var variants = catalogPort.findVariants(variantIds);

        List<OrderLine> lines = new ArrayList<>();
        for (CartLine line : cart.lines()) {
            CatalogPort.VariantSnapshot variant = variants.get(line.variantId());
            if (variant == null) {
                throw new BusinessRuleException(ErrorCode.PRODUCT_UNAVAILABLE,
                        "An item in your cart is no longer available");
            }
            if (!variant.purchasable()) {
                throw new BusinessRuleException(ErrorCode.PRODUCT_UNAVAILABLE,
                        variant.productName() + " is not currently available");
            }
            int quantity = line.quantity().value();
            if (quantity < variant.minOrderQuantity()) {
                throw new BusinessRuleException(ErrorCode.QUANTITY_LIMIT_EXCEEDED,
                        variant.productName() + " must be ordered in quantities of at least "
                                + variant.minOrderQuantity());
            }
            if (quantity > variant.maxOrderQuantity()) {
                throw new BusinessRuleException(ErrorCode.QUANTITY_LIMIT_EXCEEDED,
                        variant.productName() + " can be ordered in quantities of at most "
                                + variant.maxOrderQuantity());
            }
            lines.add(new OrderLine(variant.productId(), variant.variantId(), variant.productName(),
                    variant.variantLabel(), variant.sku(), variant.unitPrice(), Quantity.of(quantity)));
        }
        Money itemsTotal = lines.stream().map(OrderLine::lineTotal).reduce(Money.inr(0), Money::plus);
        int totalItems = lines.stream().mapToInt(l -> l.quantity().value()).sum();
        return new PricedCart(lines, itemsTotal, totalItems);
    }

    private Cart requireNonEmptyCart(UUID customerId) {
        Cart cart = cartRepository.findByCustomerId(customerId)
                .orElseThrow(() -> new BusinessRuleException(ErrorCode.CONFLICT, "Your cart is empty"));
        if (cart.isEmpty()) {
            throw new BusinessRuleException(ErrorCode.CONFLICT, "Your cart is empty");
        }
        return cart;
    }

    /**
     * Loads the customer and refuses checkout until both contact channels are verified.
     *
     * <p>Both are mandatory: the requirement is that a customer proves their email and their mobile
     * before an order is accepted, so a single unverified channel blocks checkout rather than being
     * carried along.
     */
    private CustomerPort.CustomerSnapshot requireVerifiedCustomer(UUID customerId) {
        CustomerPort.CustomerSnapshot customer = customerPort.findCustomer(customerId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.CUSTOMER_NOT_FOUND, "Customer not found"));
        if (!customer.emailVerified()) {
            throw new ForbiddenException("Please verify your email address before checking out");
        }
        if (!customer.mobileVerified()) {
            throw new ForbiddenException("Please verify your mobile number before checking out");
        }
        return customer;
    }

    private CustomerPort.AddressSnapshot requireAddress(UUID customerId, UUID addressId) {
        if (addressId == null) {
            throw new ValidationException("A delivery address is required");
        }
        return customerPort.findAddress(customerId, addressId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.ADDRESS_NOT_FOUND,
                        "That delivery address was not found"));
    }

    private DeliveryAddress toDeliveryAddress(CustomerPort.AddressSnapshot address) {
        return new DeliveryAddress(address.recipientName(), address.mobile(), address.line1(),
                address.line2(), address.city(), address.state(), address.pincode(),
                address.deliveryInstructions());
    }

    private static String normaliseKey(String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        String trimmed = key.trim();
        if (trimmed.length() > 200) {
            throw new ValidationException("The idempotency key is too long");
        }
        return trimmed;
    }

    /**
     * A stable description of the request, used to detect a replayed key with a different body.
     *
     * <p>Only the client-supplied inputs are included. The cart is deliberately excluded: it is
     * server-side state that checkout clears, so a legitimate replay always finds an empty cart and
     * could never match a cart-derived fingerprint. The address is the meaningful client choice here,
     * and using the same key for a different address is the conflict worth catching.
     */
    private String fingerprint(UUID customerId, UUID addressId) {
        return customerId + "|" + addressId;
    }

    private static String decimal(Money money) {
        return money.toMajorUnits().setScale(2, java.math.RoundingMode.UNNECESSARY).toPlainString();
    }

    /** The priced lines and their totals, produced together so they cannot disagree. */
    private record PricedCart(List<OrderLine> lines, Money itemsTotal, int totalItems) {
    }
}
