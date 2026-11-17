package com.choco.order.application.service;

import com.choco.core.domain.Money;
import com.choco.order.application.dto.CartLineView;
import com.choco.order.application.dto.CartView;
import com.choco.order.application.dto.OrderAddressView;
import com.choco.order.application.dto.OrderLineView;
import com.choco.order.application.dto.OrderNoteView;
import com.choco.order.application.dto.OrderStatusChangeView;
import com.choco.order.application.dto.OrderSummaryView;
import com.choco.order.application.dto.OrderView;
import com.choco.order.domain.cart.Cart;
import com.choco.order.domain.cart.CartLine;
import com.choco.order.domain.order.Order;
import com.choco.order.domain.order.OrderLine;
import java.math.BigDecimal;

/**
 * Turns domain aggregates into the records the API returns.
 *
 * <p>Written by hand rather than by reflecting over the aggregate, so a new field on {@code Order} is
 * not exposed until someone deliberately adds it here. That is the same reasoning as the identity
 * service's view builders: the safe default is to leak nothing.
 *
 * <p>Every money value is emitted twice — exact minor units for a programmatic caller and a decimal
 * string for display. The decimal is a {@code String}, never a {@code double}, so a client cannot
 * reintroduce a floating-point rounding error.
 */
public final class OrderViewMapper {

    private OrderViewMapper() {
    }

    public static OrderView toView(Order order) {
        return new OrderView(
                order.id(),
                order.orderNumber(),
                order.customerId(),
                order.customer().fullName(),
                order.customer().email().value(),
                order.customer().mobile().value(),
                toAddressView(order),
                order.lines().stream().map(OrderViewMapper::toLineView).toList(),
                totalItems(order),
                order.itemsTotal().minorUnits(),
                decimal(order.itemsTotal()),
                order.deliveryCharge().minorUnits(),
                decimal(order.deliveryCharge()),
                order.total().minorUnits(),
                decimal(order.total()),
                order.total().currency().getCurrencyCode(),
                order.status().name(),
                order.paymentStatus().name(),
                order.preparationStatus() == null ? null : order.preparationStatus().name(),
                order.deliveryStatus().name(),
                order.paymentId(),
                order.providerOrderId(),
                order.deliveryPersonName(),
                order.dispatchedAt(),
                order.deliveredAt(),
                order.createdAt(),
                order.updatedAt(),
                order.statusHistory().stream()
                        .map(change -> new OrderStatusChangeView(
                                change.fromStatus() == null ? null : change.fromStatus().name(),
                                change.toStatus().name(),
                                change.reason(),
                                change.occurredAt()))
                        .toList(),
                order.notes().stream()
                        .map(note -> new OrderNoteView(note.id(), note.text(), note.createdAt()))
                        .toList(),
                order.isCancellable());
    }

    public static OrderSummaryView toSummary(Order order) {
        return new OrderSummaryView(
                order.id(),
                order.orderNumber(),
                totalItems(order),
                order.total().minorUnits(),
                decimal(order.total()),
                order.total().currency().getCurrencyCode(),
                order.status().name(),
                order.paymentStatus().name(),
                order.createdAt());
    }

    public static CartView toView(Cart cart) {
        return new CartView(
                cart.id(),
                cart.lines().stream().map(OrderViewMapper::toLineView).toList(),
                cart.totalItems(),
                cart.estimatedTotal().minorUnits(),
                decimal(cart.estimatedTotal()),
                cart.estimatedTotal().currency().getCurrencyCode(),
                cart.updatedAt());
    }

    public static CartLineView toLineView(CartLine line) {
        return new CartLineView(
                line.productId(),
                line.variantId(),
                line.productName(),
                line.variantLabel(),
                line.unitPrice().minorUnits(),
                decimal(line.unitPrice()),
                line.quantity().value(),
                line.lineTotal().minorUnits(),
                decimal(line.lineTotal()));
    }

    public static OrderLineView toLineView(OrderLine line) {
        return new OrderLineView(
                line.productId(),
                line.variantId(),
                line.productName(),
                line.variantLabel(),
                line.sku(),
                line.unitPrice().minorUnits(),
                decimal(line.unitPrice()),
                line.quantity().value(),
                line.lineTotal().minorUnits(),
                decimal(line.lineTotal()));
    }

    private static OrderAddressView toAddressView(Order order) {
        var address = order.deliveryAddress();
        return new OrderAddressView(
                address.recipientName(),
                address.mobile().value(),
                address.line1(),
                address.line2(),
                address.city(),
                address.state(),
                address.pincode().value(),
                address.deliveryInstructions(),
                address.formatted());
    }

    private static int totalItems(Order order) {
        return order.lines().stream().mapToInt(line -> line.quantity().value()).sum();
    }

    private static String decimal(Money money) {
        return money.toMajorUnits().setScale(2, java.math.RoundingMode.UNNECESSARY).toPlainString();
    }
}
