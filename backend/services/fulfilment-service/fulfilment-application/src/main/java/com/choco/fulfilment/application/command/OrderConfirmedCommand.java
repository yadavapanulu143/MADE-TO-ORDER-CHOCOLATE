package com.choco.fulfilment.application.command;

import java.util.List;

/**
 * The hand-over of a paid order into the kitchen, derived from the order service's
 * {@code order.confirmed} event.
 *
 * <p>Carries everything the fulfilment service needs to open jobs and, later, a delivery, so it never
 * calls back into the order service. The delivery charge and address are the ones frozen at checkout.
 */
public record OrderConfirmedCommand(
        String orderId,
        String customerId,
        String orderNumber,
        String customerName,
        Address deliveryAddress,
        long deliveryChargeMinorUnits,
        List<Line> lines) {

    public record Address(
            String recipientName,
            String mobile,
            String line1,
            String line2,
            String city,
            String state,
            String pincode,
            String deliveryInstructions) {
    }

    public record Line(
            String productId,
            String variantId,
            String productName,
            String variantLabel,
            int quantity) {
    }
}
