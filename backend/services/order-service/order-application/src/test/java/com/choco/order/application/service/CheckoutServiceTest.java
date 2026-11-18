package com.choco.order.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.choco.core.domain.Money;
import com.choco.core.error.BusinessRuleException;
import com.choco.core.error.ErrorCode;
import com.choco.core.error.ForbiddenException;
import com.choco.core.error.IdempotencyConflictException;
import com.choco.core.error.NotFoundException;
import com.choco.core.error.ValidationException;
import com.choco.order.application.command.AddToCartCommand;
import com.choco.order.application.command.CheckoutCommand;
import com.choco.order.application.dto.CheckoutQuoteView;
import com.choco.order.application.dto.OrderView;
import com.choco.order.application.support.FixedOrderNumbers;
import com.choco.order.application.support.InMemoryCartRepository;
import com.choco.order.application.support.InMemoryIdempotencyRepository;
import com.choco.order.application.support.InMemoryOrderRepository;
import com.choco.order.application.support.StubCatalog;
import com.choco.order.application.support.StubCustomers;
import com.choco.order.application.support.StubServiceability;
import com.choco.test.Fakes;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Checkout service")
class CheckoutServiceTest {

    private static final UUID CUSTOMER = UUID.randomUUID();
    private static final UUID PRODUCT = UUID.randomUUID();
    private static final UUID VARIANT = UUID.randomUUID();
    private static final UUID ADDRESS = UUID.randomUUID();

    private StubCatalog catalog;
    private StubCustomers customers;
    private StubServiceability serviceability;
    private Fakes.RecordingEventPublisher events;
    private InMemoryCartRepository carts;
    private InMemoryOrderRepository orders;
    private InMemoryIdempotencyRepository idempotency;
    private CartService cartService;
    private CheckoutService service;

    @BeforeEach
    void setUp() {
        catalog = new StubCatalog().with(PRODUCT, VARIANT, "Dark Chocolate", "200 g", "SKU-1",
                Money.inr(49900), 1, 10);
        customers = new StubCustomers()
                .withCustomer(CUSTOMER, "Asha Rao", "asha@example.com", "9876543210", true, true)
                .withAddress(CUSTOMER, ADDRESS, "Asha Rao", "9876543210", "560001");
        serviceability = new StubServiceability().deliverable("560001", Money.inr(5000));
        carts = new InMemoryCartRepository();
        orders = new InMemoryOrderRepository();
        idempotency = new InMemoryIdempotencyRepository();

        Fakes.FixedClock clock = Fakes.FixedClock.startingAt("2026-01-01T10:00:00Z");
        events = new Fakes.RecordingEventPublisher();
        cartService = new CartService(carts, catalog, clock, new Fakes.SequentialIdGenerator(),
                new Fakes.DirectTransactionRunner());
        service = new CheckoutService(carts, orders, idempotency, catalog, customers, serviceability,
                new FixedOrderNumbers(), clock, new Fakes.SequentialIdGenerator(),
                new Fakes.DirectTransactionRunner(), events);
    }

    @Test
    @DisplayName("a quote prices the cart and adds the delivery charge")
    void quotePrices() {
        cartService.addLine(CUSTOMER, new AddToCartCommand(VARIANT, 2));

        CheckoutQuoteView quote = service.quote(CUSTOMER, ADDRESS);

        assertThat(quote.serviceable()).isTrue();
        assertThat(quote.itemsTotalMinorUnits()).isEqualTo(99800);
        assertThat(quote.deliveryChargeMinorUnits()).isEqualTo(5000);
        assertThat(quote.totalMinorUnits()).isEqualTo(104800);
        assertThat(quote.total()).isEqualTo("1048.00");
    }

    @Test
    @DisplayName("a quote for an undeliverable PIN code is marked not serviceable")
    void quoteNotServiceable() {
        UUID remote = UUID.randomUUID();
        customers.withAddressInPincode(CUSTOMER, remote, "999999");
        cartService.addLine(CUSTOMER, new AddToCartCommand(VARIANT, 1));

        CheckoutQuoteView quote = service.quote(CUSTOMER, remote);

        assertThat(quote.serviceable()).isFalse();
        assertThat(quote.serviceabilityMessage()).contains("999999");
        assertThat(quote.deliveryChargeMinorUnits()).isZero();
    }

    @Test
    @DisplayName("placing an order freezes the price, the address and the totals")
    void placeOrderSnapshot() {
        cartService.addLine(CUSTOMER, new AddToCartCommand(VARIANT, 2));

        OrderView order = service.placeOrder(CUSTOMER, new CheckoutCommand(ADDRESS, null));

        assertThat(order.orderNumber()).isEqualTo("CHOCO-00001");
        assertThat(order.status()).isEqualTo("PAYMENT_PENDING");
        assertThat(order.paymentStatus()).isEqualTo("PENDING");
        assertThat(order.lines()).hasSize(1);
        assertThat(order.lines().get(0).unitPriceMinorUnits()).isEqualTo(49900);
        assertThat(order.itemsTotalMinorUnits()).isEqualTo(99800);
        assertThat(order.deliveryChargeMinorUnits()).isEqualTo(5000);
        assertThat(order.totalMinorUnits()).isEqualTo(104800);
        assertThat(order.deliveryAddress().pincode()).isEqualTo("560001");
        assertThat(order.deliveryAddress().recipientName()).isEqualTo("Asha Rao");
    }

    @Test
    @DisplayName("placing an order publishes exactly one order.placed event carrying the frozen total")
    void placeOrderPublishesOrderPlaced() {
        cartService.addLine(CUSTOMER, new AddToCartCommand(VARIANT, 2));

        OrderView order = service.placeOrder(CUSTOMER, new CheckoutCommand(ADDRESS, null));

        var placed = events.eventsOfType("order.placed");
        assertThat(placed).hasSize(1);
        assertThat(placed.get(0).aggregateId()).isEqualTo(order.id().toString());
        assertThat(placed.get(0)).isInstanceOfSatisfying(
                com.choco.order.domain.event.OrderPlaced.class, event -> {
                    assertThat(event.customerId()).isEqualTo(CUSTOMER.toString());
                    assertThat(event.orderNumber()).isEqualTo("CHOCO-00001");
                    assertThat(event.totalMinorUnits()).isEqualTo(104800);
                    assertThat(event.currency()).isEqualTo("INR");
                    assertThat(event.lineCount()).isEqualTo(1);
                });
    }

    @Test
    @DisplayName("a checkout replay publishes nothing, because no new order was placed")
    void checkoutReplayPublishesNothing() {
        cartService.addLine(CUSTOMER, new AddToCartCommand(VARIANT, 2));
        service.placeOrder(CUSTOMER, new CheckoutCommand(ADDRESS, "key-1"));
        events.clear();

        service.placeOrder(CUSTOMER, new CheckoutCommand(ADDRESS, "key-1"));

        assertThat(events.events()).isEmpty();
    }

    @Test
    @DisplayName("placing an order clears the cart")
    void placeOrderClearsCart() {
        cartService.addLine(CUSTOMER, new AddToCartCommand(VARIANT, 2));
        service.placeOrder(CUSTOMER, new CheckoutCommand(ADDRESS, null));

        assertThat(cartService.view(CUSTOMER).lines()).isEmpty();
    }

    @Test
    @DisplayName("a later catalogue price change does not alter a placed order")
    void priceChangeDoesNotAlterPlacedOrder() {
        cartService.addLine(CUSTOMER, new AddToCartCommand(VARIANT, 2));
        OrderView placed = service.placeOrder(CUSTOMER, new CheckoutCommand(ADDRESS, null));

        // The seller raises the price and the cart is refilled and checked out again.
        catalog.with(PRODUCT, VARIANT, "Dark Chocolate", "200 g", "SKU-1", Money.inr(59900), 1, 10);
        cartService.addLine(CUSTOMER, new AddToCartCommand(VARIANT, 2));
        OrderView second = service.placeOrder(CUSTOMER, new CheckoutCommand(ADDRESS, null));

        assertThat(orders.findById(placed.id()).orElseThrow().lines().get(0).unitPrice())
                .isEqualTo(Money.inr(49900));
        assertThat(second.lines().get(0).unitPriceMinorUnits()).isEqualTo(59900);
    }

    @Test
    @DisplayName("an empty cart cannot be checked out")
    void emptyCartRefused() {
        assertThatThrownBy(() -> service.placeOrder(CUSTOMER, new CheckoutCommand(ADDRESS, null)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("empty");
    }

    @Test
    @DisplayName("checkout requires both email and mobile verification")
    void checkoutRequiresVerification() {
        cartService.addLine(CUSTOMER, new AddToCartCommand(VARIANT, 1));
        UUID unverified = UUID.randomUUID();
        customers.withCustomer(unverified, "New User", "new@example.com", "9876500000", true, false)
                .withAddress(unverified, ADDRESS, "New User", "9876500000", "560001");
        cartService.addLine(unverified, new AddToCartCommand(VARIANT, 1));

        assertThatThrownBy(() -> service.placeOrder(unverified, new CheckoutCommand(ADDRESS, null)))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("mobile");
    }

    @Test
    @DisplayName("an unverified email blocks checkout")
    void checkoutRequiresEmailVerification() {
        UUID unverified = UUID.randomUUID();
        customers.withCustomer(unverified, "New User", "new@example.com", "9876500000", false, true)
                .withAddress(unverified, ADDRESS, "New User", "9876500000", "560001");
        cartService.addLine(unverified, new AddToCartCommand(VARIANT, 1));

        assertThatThrownBy(() -> service.placeOrder(unverified, new CheckoutCommand(ADDRESS, null)))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("email");
    }

    @Test
    @DisplayName("checkout is refused for an undeliverable PIN code")
    void checkoutRefusedWhenNotServiceable() {
        UUID remote = UUID.randomUUID();
        customers.withAddressInPincode(CUSTOMER, remote, "999999");
        cartService.addLine(CUSTOMER, new AddToCartCommand(VARIANT, 1));

        assertThatThrownBy(() -> service.placeOrder(CUSTOMER, new CheckoutCommand(remote, null)))
                .isInstanceOf(BusinessRuleException.class)
                .extracting(ex -> ((BusinessRuleException) ex).errorCode())
                .isEqualTo(ErrorCode.PINCODE_NOT_SERVICEABLE);
    }

    @Test
    @DisplayName("an address that is not the caller's is refused")
    void addressMustBelongToCaller() {
        UUID otherCustomer = UUID.randomUUID();
        customers.withCustomer(otherCustomer, "Other", "other@example.com", "9876500001", true, true);
        UUID otherAddress = UUID.randomUUID();
        customers.withAddressInPincode(otherCustomer, otherAddress, "560001");
        cartService.addLine(CUSTOMER, new AddToCartCommand(VARIANT, 1));

        assertThatThrownBy(() -> service.placeOrder(CUSTOMER, new CheckoutCommand(otherAddress, null)))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("a missing address id is a validation error")
    void missingAddress() {
        cartService.addLine(CUSTOMER, new AddToCartCommand(VARIANT, 1));
        assertThatThrownBy(() -> service.placeOrder(CUSTOMER, new CheckoutCommand(null, null)))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    @DisplayName("a quantity below the variant minimum is refused")
    void belowMinimum() {
        UUID strict = UUID.randomUUID();
        catalog.with(PRODUCT, strict, "Gift Box", "500 g", "SKU-2", Money.inr(99900), 3, 10);
        cartService.addLine(CUSTOMER, new AddToCartCommand(strict, 1));

        assertThatThrownBy(() -> service.placeOrder(CUSTOMER, new CheckoutCommand(ADDRESS, null)))
                .isInstanceOf(BusinessRuleException.class)
                .extracting(ex -> ((BusinessRuleException) ex).errorCode())
                .isEqualTo(ErrorCode.QUANTITY_LIMIT_EXCEEDED);
    }

    @Test
    @DisplayName("a quantity above the variant maximum is refused")
    void aboveMaximum() {
        UUID strict = UUID.randomUUID();
        catalog.with(PRODUCT, strict, "Gift Box", "500 g", "SKU-2", Money.inr(99900), 1, 4);
        cartService.addLine(CUSTOMER, new AddToCartCommand(strict, 5));

        assertThatThrownBy(() -> service.placeOrder(CUSTOMER, new CheckoutCommand(ADDRESS, null)))
                .isInstanceOf(BusinessRuleException.class)
                .extracting(ex -> ((BusinessRuleException) ex).errorCode())
                .isEqualTo(ErrorCode.QUANTITY_LIMIT_EXCEEDED);
    }

    @Test
    @DisplayName("a variant withdrawn between adding and checkout is refused")
    void withdrawnBetweenAddAndCheckout() {
        cartService.addLine(CUSTOMER, new AddToCartCommand(VARIANT, 1));
        catalog.withUnavailable(PRODUCT, VARIANT, "Dark Chocolate", "200 g", Money.inr(49900));

        assertThatThrownBy(() -> service.placeOrder(CUSTOMER, new CheckoutCommand(ADDRESS, null)))
                .isInstanceOf(BusinessRuleException.class)
                .extracting(ex -> ((BusinessRuleException) ex).errorCode())
                .isEqualTo(ErrorCode.PRODUCT_UNAVAILABLE);
    }

    @Test
    @DisplayName("a variant removed from the catalogue between adding and checkout is refused")
    void removedBetweenAddAndCheckout() {
        cartService.addLine(CUSTOMER, new AddToCartCommand(VARIANT, 1));
        catalog.remove(VARIANT);

        assertThatThrownBy(() -> service.placeOrder(CUSTOMER, new CheckoutCommand(ADDRESS, null)))
                .isInstanceOf(BusinessRuleException.class)
                .extracting(ex -> ((BusinessRuleException) ex).errorCode())
                .isEqualTo(ErrorCode.PRODUCT_UNAVAILABLE);
    }

    @Test
    @DisplayName("replaying the same idempotency key returns the same order, not a second one")
    void idempotentReplay() {
        cartService.addLine(CUSTOMER, new AddToCartCommand(VARIANT, 2));

        OrderView first = service.placeOrder(CUSTOMER, new CheckoutCommand(ADDRESS, "key-1"));
        OrderView second = service.placeOrder(CUSTOMER, new CheckoutCommand(ADDRESS, "key-1"));

        assertThat(second.id()).isEqualTo(first.id());
        assertThat(orders.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("a replay with a different address under the same key is refused")
    void conflictingReplayRefused() {
        cartService.addLine(CUSTOMER, new AddToCartCommand(VARIANT, 2));
        UUID secondAddress = UUID.randomUUID();
        customers.withAddressInPincode(CUSTOMER, secondAddress, "560001");

        service.placeOrder(CUSTOMER, new CheckoutCommand(ADDRESS, "key-2"));

        assertThatThrownBy(() -> service.placeOrder(CUSTOMER, new CheckoutCommand(secondAddress, "key-2")))
                .isInstanceOf(IdempotencyConflictException.class);
    }

    @Test
    @DisplayName("a replay after the cart was cleared still returns the original order")
    void replayAfterCartClearedReturnsOriginal() {
        cartService.addLine(CUSTOMER, new AddToCartCommand(VARIANT, 2));
        OrderView first = service.placeOrder(CUSTOMER, new CheckoutCommand(ADDRESS, "key-3"));

        // Checkout cleared the cart; a retry arrives with an empty cart but the same key and address.
        OrderView replay = service.placeOrder(CUSTOMER, new CheckoutCommand(ADDRESS, "key-3"));

        assertThat(replay.id()).isEqualTo(first.id());
        assertThat(orders.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("an order placed without a key is not deduplicated")
    void noKeyMeansNoDedup() {
        cartService.addLine(CUSTOMER, new AddToCartCommand(VARIANT, 1));
        service.placeOrder(CUSTOMER, new CheckoutCommand(ADDRESS, null));
        cartService.addLine(CUSTOMER, new AddToCartCommand(VARIANT, 1));
        service.placeOrder(CUSTOMER, new CheckoutCommand(ADDRESS, null));

        assertThat(orders.count()).isEqualTo(2);
    }

    @Test
    @DisplayName("order numbers increment across orders")
    void orderNumbersIncrement() {
        cartService.addLine(CUSTOMER, new AddToCartCommand(VARIANT, 1));
        OrderView first = service.placeOrder(CUSTOMER, new CheckoutCommand(ADDRESS, null));
        cartService.addLine(CUSTOMER, new AddToCartCommand(VARIANT, 1));
        OrderView second = service.placeOrder(CUSTOMER, new CheckoutCommand(ADDRESS, null));

        assertThat(first.orderNumber()).isEqualTo("CHOCO-00001");
        assertThat(second.orderNumber()).isEqualTo("CHOCO-00002");
    }
}
