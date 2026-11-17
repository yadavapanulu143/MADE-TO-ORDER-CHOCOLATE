package com.choco.order.application.dto;

import java.util.List;

/**
 * The priced result of a checkout validation, before an order exists.
 *
 * <p>Lets the storefront show the customer exactly what they will pay — items, delivery charge and
 * total — and the reason it cannot proceed, if any. The same validation runs again when the order is
 * actually placed, so a quote that was displayed and then tampered with has no effect.
 */
public record CheckoutQuoteView(
        boolean serviceable,
        String serviceabilityMessage,
        List<OrderLineView> lines,
        int totalItems,
        long itemsTotalMinorUnits,
        String itemsTotal,
        long deliveryChargeMinorUnits,
        String deliveryCharge,
        long totalMinorUnits,
        String total,
        String currency) {
}
