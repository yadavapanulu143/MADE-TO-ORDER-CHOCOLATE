package com.choco.order.domain.cart;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.choco.core.domain.Money;
import com.choco.core.error.BusinessRuleException;
import com.choco.core.error.ErrorCode;
import com.choco.core.error.NotFoundException;
import com.choco.core.error.ValidationException;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Cart aggregate")
class CartTest {

    private static final Instant T0 = Instant.parse("2026-01-01T10:00:00Z");
    private static final UUID CUSTOMER = UUID.randomUUID();
    private static final UUID PRODUCT = UUID.randomUUID();
    private static final UUID VARIANT_A = UUID.randomUUID();
    private static final UUID VARIANT_B = UUID.randomUUID();

    private Cart cart;

    @BeforeEach
    void setUp() {
        cart = Cart.empty(UUID.randomUUID(), CUSTOMER, T0);
    }

    @Test
    @DisplayName("a new cart is empty")
    void newCartIsEmpty() {
        assertThat(cart.isEmpty()).isTrue();
        assertThat(cart.totalItems()).isZero();
        assertThat(cart.estimatedTotal()).isEqualTo(Money.inr(0));
    }

    @Test
    @DisplayName("adding a variant creates a line")
    void addLine() {
        cart.addLine(PRODUCT, VARIANT_A, "Dark Chocolate", "200 g", Money.inr(49900), 2, T0);

        assertThat(cart.lines()).hasSize(1);
        assertThat(cart.totalItems()).isEqualTo(2);
        assertThat(cart.estimatedTotal()).isEqualTo(Money.inr(99800));
    }

    @Test
    @DisplayName("adding the same variant again increases its quantity rather than adding a line")
    void addSameVariantIncrements() {
        cart.addLine(PRODUCT, VARIANT_A, "Dark Chocolate", "200 g", Money.inr(49900), 2, T0);
        cart.addLine(PRODUCT, VARIANT_A, "Dark Chocolate", "200 g", Money.inr(49900), 3, T0);

        assertThat(cart.lines()).hasSize(1);
        assertThat(cart.lines().get(0).quantity().value()).isEqualTo(5);
    }

    @Test
    @DisplayName("re-adding refreshes the displayed name and price from the catalogue")
    void reAddRefreshesPrice() {
        cart.addLine(PRODUCT, VARIANT_A, "Old Name", "200 g", Money.inr(49900), 1, T0);
        cart.addLine(PRODUCT, VARIANT_A, "New Name", "250 g", Money.inr(55000), 1, T0);

        assertThat(cart.lines().get(0).productName()).isEqualTo("New Name");
        assertThat(cart.lines().get(0).unitPrice()).isEqualTo(Money.inr(55000));
    }

    @Test
    @DisplayName("a quantity above the per-line ceiling is refused, not truncated")
    void quantityCeiling() {
        assertThatThrownBy(() -> cart.addLine(PRODUCT, VARIANT_A, "Dark", "200 g", Money.inr(100),
                Cart.MAX_QUANTITY_PER_LINE + 1, T0))
                .isInstanceOf(BusinessRuleException.class)
                .extracting(ex -> ((BusinessRuleException) ex).errorCode())
                .isEqualTo(ErrorCode.QUANTITY_LIMIT_EXCEEDED);
    }

    @Test
    @DisplayName("incrementing past the ceiling is refused")
    void incrementPastCeiling() {
        cart.addLine(PRODUCT, VARIANT_A, "Dark", "200 g", Money.inr(100), Cart.MAX_QUANTITY_PER_LINE, T0);
        assertThatThrownBy(() -> cart.addLine(PRODUCT, VARIANT_A, "Dark", "200 g", Money.inr(100), 1, T0))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    @DisplayName("a zero or negative quantity is refused")
    void rejectsNonPositiveQuantity() {
        assertThatThrownBy(() -> cart.addLine(PRODUCT, VARIANT_A, "Dark", "200 g", Money.inr(100), 0, T0))
                .isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> cart.addLine(PRODUCT, VARIANT_A, "Dark", "200 g", Money.inr(100), -1, T0))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    @DisplayName("the line ceiling is enforced")
    void lineCeiling() {
        for (int i = 0; i < Cart.MAX_LINES; i++) {
            cart.addLine(PRODUCT, UUID.randomUUID(), "Dark", "200 g", Money.inr(100), 1, T0);
        }
        assertThatThrownBy(() -> cart.addLine(PRODUCT, UUID.randomUUID(), "Dark", "200 g",
                Money.inr(100), 1, T0))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("different items");
    }

    @Test
    @DisplayName("setting an absolute quantity replaces the line quantity")
    void setQuantity() {
        cart.addLine(PRODUCT, VARIANT_A, "Dark", "200 g", Money.inr(100), 2, T0);
        cart.setQuantity(VARIANT_A, 7, T0);

        assertThat(cart.lines().get(0).quantity().value()).isEqualTo(7);
    }

    @Test
    @DisplayName("setting a quantity on a variant not in the cart is a not-found")
    void setQuantityUnknownVariant() {
        assertThatThrownBy(() -> cart.setQuantity(VARIANT_A, 1, T0))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("setting a quantity above the ceiling is refused")
    void setQuantityCeiling() {
        cart.addLine(PRODUCT, VARIANT_A, "Dark", "200 g", Money.inr(100), 1, T0);
        assertThatThrownBy(() -> cart.setQuantity(VARIANT_A, Cart.MAX_QUANTITY_PER_LINE + 1, T0))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    @DisplayName("removing a line empties it from the cart")
    void removeLine() {
        cart.addLine(PRODUCT, VARIANT_A, "Dark", "200 g", Money.inr(100), 1, T0);
        cart.addLine(PRODUCT, VARIANT_B, "Milk", "100 g", Money.inr(100), 1, T0);

        cart.removeLine(VARIANT_A, T0);

        assertThat(cart.lines()).hasSize(1);
        assertThat(cart.lines().get(0).variantId()).isEqualTo(VARIANT_B);
    }

    @Test
    @DisplayName("removing a variant not in the cart is a not-found")
    void removeUnknownLine() {
        assertThatThrownBy(() -> cart.removeLine(VARIANT_A, T0))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("clearing empties the cart")
    void clear() {
        cart.addLine(PRODUCT, VARIANT_A, "Dark", "200 g", Money.inr(100), 3, T0);
        cart.clear(T0);

        assertThat(cart.isEmpty()).isTrue();
    }

    @Test
    @DisplayName("ownership is enforced")
    void ownership() {
        cart.assertOwnedBy(CUSTOMER);
        assertThatThrownBy(() -> cart.assertOwnedBy(UUID.randomUUID()))
                .isInstanceOf(BusinessRuleException.class)
                .extracting(ex -> ((BusinessRuleException) ex).errorCode())
                .isEqualTo(ErrorCode.FORBIDDEN);
    }

    @Test
    @DisplayName("total items and estimated total sum every line")
    void totals() {
        cart.addLine(PRODUCT, VARIANT_A, "Dark", "200 g", Money.inr(49900), 2, T0);
        cart.addLine(PRODUCT, VARIANT_B, "Milk", "100 g", Money.inr(25050), 4, T0);

        assertThat(cart.totalItems()).isEqualTo(6);
        assertThat(cart.estimatedTotal()).isEqualTo(Money.inr(2 * 49900 + 4 * 25050));
    }
}
