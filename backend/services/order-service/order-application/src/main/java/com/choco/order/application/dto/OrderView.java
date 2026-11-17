package com.choco.order.application.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * A complete order as presented to a customer or the seller.
 *
 * <p>Carries the three status tracks separately as well as the overall status, because the customer
 * tracking page wants "preparation: packaging" while the seller's dashboard filters on the overall
 * status. The payment ids are included for the seller's payment view; no card or UPI detail exists
 * anywhere in this service to leak.
 */
public record OrderView(
        UUID id,
        String orderNumber,
        UUID customerId,
        String customerName,
        String customerEmail,
        String customerMobile,
        OrderAddressView deliveryAddress,
        List<OrderLineView> lines,
        int totalItems,
        long itemsTotalMinorUnits,
        String itemsTotal,
        long deliveryChargeMinorUnits,
        String deliveryCharge,
        long totalMinorUnits,
        String total,
        String currency,
        String status,
        String paymentStatus,
        String preparationStatus,
        String deliveryStatus,
        String paymentId,
        String providerOrderId,
        String deliveryPersonName,
        Instant dispatchedAt,
        Instant deliveredAt,
        Instant createdAt,
        Instant updatedAt,
        List<OrderStatusChangeView> statusHistory,
        List<OrderNoteView> notes,
        boolean cancellable) {
}
