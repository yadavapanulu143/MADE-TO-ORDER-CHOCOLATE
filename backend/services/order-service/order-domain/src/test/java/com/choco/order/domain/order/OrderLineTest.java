package com.choco.order.domain.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.choco.core.domain.Money;
import com.choco.core.domain.Quantity;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Order line value object")
class OrderLineTest {

    private static final UUID PRODUCT = UUID.randomUUID();
    private static final UUID VARIANT = UUID.randomUUID();

    @Test
    @DisplayName("computes its line total exactly")
    void lineTotal() {
        OrderLine line = new OrderLine(PRODUCT, VARIANT, "Dark", "200 g", "sku-1", Money.inr(49900),
                Quantity.of(3));
        assertThat(line.lineTotal()).isEqualTo(Money.inr(149700));
    }

    @Test
    @DisplayName("normalises the SKU to upper case and allows a missing one")
    void normalisesSku() {
        assertThat(line("sku-abc").sku()).isEqualTo("SKU-ABC");
        assertThat(new OrderLine(PRODUCT, VARIANT, "Dark", "200 g", null, Money.inr(100),
                Quantity.of(1)).sku()).isNull();
        assertThat(new OrderLine(PRODUCT, VARIANT, "Dark", "200 g", "  ", Money.inr(100),
                Quantity.of(1)).sku()).isNull();
    }

    @Test
    @DisplayName("collapses whitespace in the name and label")
    void normalisesText() {
        OrderLine line = new OrderLine(PRODUCT, VARIANT, "  Dark   Chocolate  ", " 200   g  ",
                null, Money.inr(100), Quantity.of(1));
        assertThat(line.productName()).isEqualTo("Dark Chocolate");
        assertThat(line.variantLabel()).isEqualTo("200 g");
    }

    @Test
    @DisplayName("rejects a missing product or variant id")
    void rejectsMissingIds() {
        assertThatThrownBy(() -> new OrderLine(null, VARIANT, "Dark", "200 g", null, Money.inr(100),
                Quantity.of(1))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new OrderLine(PRODUCT, null, "Dark", "200 g", null, Money.inr(100),
                Quantity.of(1))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("rejects a blank name or label")
    void rejectsBlankText() {
        assertThatThrownBy(() -> new OrderLine(PRODUCT, VARIANT, "  ", "200 g", null, Money.inr(100),
                Quantity.of(1))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new OrderLine(PRODUCT, VARIANT, "Dark", "  ", null, Money.inr(100),
                Quantity.of(1))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("rejects a zero or negative price")
    void rejectsBadPrice() {
        assertThatThrownBy(() -> new OrderLine(PRODUCT, VARIANT, "Dark", "200 g", null, Money.inr(0),
                Quantity.of(1))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new OrderLine(PRODUCT, VARIANT, "Dark", "200 g", null, Money.inr(-5),
                Quantity.of(1))).isInstanceOf(IllegalArgumentException.class);
    }

    private static OrderLine line(String sku) {
        return new OrderLine(PRODUCT, VARIANT, "Dark", "200 g", sku, Money.inr(100), Quantity.of(1));
    }
}
