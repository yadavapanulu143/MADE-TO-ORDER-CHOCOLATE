package com.choco.order.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.choco.core.domain.Money;
import com.choco.core.error.BusinessRuleException;
import com.choco.core.error.ErrorCode;
import com.choco.core.error.NotFoundException;
import com.choco.core.error.ValidationException;
import com.choco.order.application.command.AddToCartCommand;
import com.choco.order.application.command.UpdateCartLineCommand;
import com.choco.order.application.dto.CartView;
import com.choco.order.application.support.InMemoryCartRepository;
import com.choco.order.application.support.StubCatalog;
import com.choco.test.Fakes;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Cart service")
class CartServiceTest {

    private static final UUID CUSTOMER = UUID.randomUUID();
    private static final UUID PRODUCT = UUID.randomUUID();
    private static final UUID VARIANT = UUID.randomUUID();

    private StubCatalog catalog;
    private InMemoryCartRepository carts;
    private CartService service;

    @BeforeEach
    void setUp() {
        catalog = new StubCatalog().with(PRODUCT, VARIANT, "Dark Chocolate", "200 g", "SKU-1",
                Money.inr(49900), 1, 10);
        carts = new InMemoryCartRepository();
        service = new CartService(carts, catalog, Fakes.FixedClock.startingAt("2026-01-01T10:00:00Z"),
                new Fakes.SequentialIdGenerator(), new Fakes.DirectTransactionRunner());
    }

    @Test
    @DisplayName("viewing a cart for the first time creates an empty one")
    void firstViewCreatesEmptyCart() {
        CartView view = service.view(CUSTOMER);
        assertThat(view.lines()).isEmpty();
        assertThat(view.estimatedTotalMinorUnits()).isZero();
    }

    @Test
    @DisplayName("adding a purchasable variant creates a priced line")
    void addLine() {
        CartView view = service.addLine(CUSTOMER, new AddToCartCommand(VARIANT, 2));

        assertThat(view.lines()).hasSize(1);
        assertThat(view.lines().get(0).productName()).isEqualTo("Dark Chocolate");
        assertThat(view.lines().get(0).unitPriceMinorUnits()).isEqualTo(49900);
        assertThat(view.lines().get(0).quantity()).isEqualTo(2);
        assertThat(view.lines().get(0).lineTotal()).isEqualTo("998.00");
        assertThat(view.estimatedTotalMinorUnits()).isEqualTo(99800);
    }

    @Test
    @DisplayName("adding the same variant twice increases one line")
    void addSameVariantIncrements() {
        service.addLine(CUSTOMER, new AddToCartCommand(VARIANT, 2));
        CartView view = service.addLine(CUSTOMER, new AddToCartCommand(VARIANT, 3));

        assertThat(view.lines()).hasSize(1);
        assertThat(view.totalItems()).isEqualTo(5);
    }

    @Test
    @DisplayName("an unknown variant is refused as not found")
    void unknownVariant() {
        assertThatThrownBy(() -> service.addLine(CUSTOMER, new AddToCartCommand(UUID.randomUUID(), 1)))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("an inactive variant is refused as unavailable, not as missing")
    void inactiveVariant() {
        UUID unavailable = UUID.randomUUID();
        catalog.withUnavailable(PRODUCT, unavailable, "Milk Chocolate", "100 g", Money.inr(19900));

        assertThatThrownBy(() -> service.addLine(CUSTOMER, new AddToCartCommand(unavailable, 1)))
                .isInstanceOf(BusinessRuleException.class)
                .extracting(ex -> ((BusinessRuleException) ex).errorCode())
                .isEqualTo(ErrorCode.PRODUCT_UNAVAILABLE);
    }

    @Test
    @DisplayName("a withdrawn product is refused as unavailable")
    void withdrawnProduct() {
        UUID withdrawn = UUID.randomUUID();
        catalog.withWithdrawnProduct(PRODUCT, withdrawn, "Truffle", "Box", Money.inr(99900));

        assertThatThrownBy(() -> service.addLine(CUSTOMER, new AddToCartCommand(withdrawn, 1)))
                .isInstanceOf(BusinessRuleException.class)
                .extracting(ex -> ((BusinessRuleException) ex).errorCode())
                .isEqualTo(ErrorCode.PRODUCT_UNAVAILABLE);
    }

    @Test
    @DisplayName("a zero quantity is refused")
    void zeroQuantity() {
        assertThatThrownBy(() -> service.addLine(CUSTOMER, new AddToCartCommand(VARIANT, 0)))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    @DisplayName("updating a line quantity replaces it")
    void updateLine() {
        service.addLine(CUSTOMER, new AddToCartCommand(VARIANT, 2));
        CartView view = service.updateLine(CUSTOMER, new UpdateCartLineCommand(VARIANT, 7));

        assertThat(view.lines().get(0).quantity()).isEqualTo(7);
    }

    @Test
    @DisplayName("updating a line that is not in the cart is a not-found")
    void updateUnknownLine() {
        assertThatThrownBy(() -> service.updateLine(CUSTOMER, new UpdateCartLineCommand(VARIANT, 1)))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("removing a line empties it")
    void removeLine() {
        service.addLine(CUSTOMER, new AddToCartCommand(VARIANT, 2));
        CartView view = service.removeLine(CUSTOMER, VARIANT);

        assertThat(view.lines()).isEmpty();
    }

    @Test
    @DisplayName("clearing empties the cart")
    void clear() {
        service.addLine(CUSTOMER, new AddToCartCommand(VARIANT, 2));
        CartView view = service.clear(CUSTOMER);

        assertThat(view.lines()).isEmpty();
    }

    @Test
    @DisplayName("a customer's cart is separate from another's")
    void cartsAreIsolated() {
        UUID other = UUID.randomUUID();
        service.addLine(CUSTOMER, new AddToCartCommand(VARIANT, 2));

        CartView otherView = service.view(other);
        assertThat(otherView.lines()).isEmpty();
    }
}
