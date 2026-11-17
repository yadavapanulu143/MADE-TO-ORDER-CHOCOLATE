package com.choco.order.adapter.out.persistence;

import com.choco.core.domain.EmailAddress;
import com.choco.core.domain.Money;
import com.choco.core.domain.PhoneNumber;
import com.choco.core.domain.Pincode;
import com.choco.core.domain.Quantity;
import com.choco.order.domain.cart.Cart;
import com.choco.order.domain.cart.CartLine;
import com.choco.order.domain.idempotency.IdempotencyRecord;
import com.choco.order.domain.order.CustomerSnapshot;
import com.choco.order.domain.order.DeliveryAddress;
import com.choco.order.domain.order.DeliveryStatus;
import com.choco.order.domain.order.Order;
import com.choco.order.domain.order.OrderLine;
import com.choco.order.domain.order.OrderNote;
import com.choco.order.domain.order.OrderStatus;
import com.choco.order.domain.order.PaymentStatus;
import com.choco.order.domain.order.PreparationStatus;
import com.choco.order.domain.order.StatusChange;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Translates between the JPA entities and the domain aggregates.
 *
 * <p>Explicit rather than reflective, so the compiler catches a mismatch and a reviewer can see, in
 * one file, exactly which column feeds which business field. A mapping library would infer the
 * translation from field names, and a column rename could then change the domain silently.
 *
 * <p>Every load path uses {@code rehydrate}, never {@code place}, so reading an order cannot re-run
 * creation rules or re-raise a creation event.
 */
final class OrderPersistenceMapper {

    private OrderPersistenceMapper() {
    }

    // ---------------------------------------------------------------------------------------------
    // Order
    // ---------------------------------------------------------------------------------------------

    static OrderJpaEntity toEntity(Order order) {
        OrderJpaEntity entity = new OrderJpaEntity();
        entity.setId(order.id());
        entity.setOrderNumber(order.orderNumber());
        entity.setCustomerId(order.customerId());
        entity.setCustomerName(order.customer().fullName());
        entity.setCustomerEmail(order.customer().email().value());
        entity.setCustomerMobile(order.customer().mobile().value());

        DeliveryAddress address = order.deliveryAddress();
        entity.setAddressRecipientName(address.recipientName());
        entity.setAddressMobile(address.mobile().value());
        entity.setAddressLine1(address.line1());
        entity.setAddressLine2(address.line2());
        entity.setAddressCity(address.city());
        entity.setAddressState(address.state());
        entity.setAddressPincode(address.pincode().value());
        entity.setAddressInstructions(address.deliveryInstructions());

        entity.setItemsTotalMinorUnits(order.itemsTotal().minorUnits());
        entity.setDeliveryChargeMinorUnits(order.deliveryCharge().minorUnits());
        entity.setTotalMinorUnits(order.total().minorUnits());
        entity.setCurrency(order.total().currency().getCurrencyCode());

        entity.setStatus(order.status().name());
        entity.setPaymentStatus(order.paymentStatus().name());
        entity.setPreparationStatus(order.preparationStatus() == null ? null
                : order.preparationStatus().name());
        entity.setDeliveryStatus(order.deliveryStatus().name());

        entity.setPaymentId(order.paymentId());
        entity.setProviderOrderId(order.providerOrderId());
        entity.setDeliveryPersonName(order.deliveryPersonName());
        entity.setDeliveryPersonPhone(order.deliveryPersonPhone());
        entity.setDispatchedAt(order.dispatchedAt());
        entity.setDeliveredAt(order.deliveredAt());
        entity.setCreatedAt(order.createdAt());
        entity.setUpdatedAt(order.updatedAt());

        List<OrderLineJpaEntity> lines = new ArrayList<>();
        List<OrderLine> domainLines = order.lines();
        for (int i = 0; i < domainLines.size(); i++) {
            lines.add(toEntity(order.id(), domainLines.get(i), i));
        }
        entity.setLines(lines);

        List<OrderStatusHistoryJpaEntity> history = new ArrayList<>();
        List<StatusChange> changes = order.statusHistory();
        for (int i = 0; i < changes.size(); i++) {
            history.add(toEntity(entity, changes.get(i), i));
        }
        entity.setStatusHistory(history);

        List<OrderNoteJpaEntity> notes = new ArrayList<>();
        for (OrderNote note : order.notes()) {
            notes.add(toEntity(entity, note));
        }
        entity.setNotes(notes);

        return entity;
    }

    static Order toDomain(OrderJpaEntity entity) {
        CustomerSnapshot customer = new CustomerSnapshot(entity.getCustomerName(),
                EmailAddress.of(entity.getCustomerEmail()), PhoneNumber.of(entity.getCustomerMobile()));
        DeliveryAddress address = new DeliveryAddress(entity.getAddressRecipientName(),
                PhoneNumber.of(entity.getAddressMobile()), entity.getAddressLine1(), entity.getAddressLine2(),
                entity.getAddressCity(), entity.getAddressState(), Pincode.of(entity.getAddressPincode()),
                entity.getAddressInstructions());

        List<OrderLine> lines = entity.getLines().stream()
                .map(OrderPersistenceMapper::toDomain)
                .toList();

        Order order = Order.rehydrate(entity.getId(), entity.getOrderNumber(), entity.getCustomerId(),
                customer, address, lines, Money.ofMinorUnits(entity.getDeliveryChargeMinorUnits(),
                        java.util.Currency.getInstance(entity.getCurrency())),
                OrderStatus.valueOf(entity.getStatus()), PaymentStatus.valueOf(entity.getPaymentStatus()),
                entity.getPreparationStatus() == null ? null
                        : PreparationStatus.valueOf(entity.getPreparationStatus()),
                DeliveryStatus.valueOf(entity.getDeliveryStatus()), entity.getPaymentId(),
                entity.getProviderOrderId(), entity.getDeliveryPersonName(),
                entity.getDeliveryPersonPhone(), entity.getDispatchedAt(), entity.getDeliveredAt(),
                entity.getCreatedAt(), entity.getUpdatedAt());

        order.attachStatusHistory(entity.getStatusHistory().stream()
                .map(OrderPersistenceMapper::toDomain).toList());
        order.attachNotes(entity.getNotes().stream().map(OrderPersistenceMapper::toDomain).toList());
        return order;
    }

    private static OrderLineJpaEntity toEntity(UUID orderId, OrderLine line, int displayOrder) {
        OrderLineJpaEntity entity = new OrderLineJpaEntity();
        // A line has no identity of its own in the domain, but the row needs a primary key. Deriving
        // it from the order and the variant keeps it stable across saves, so an update is an update
        // rather than a delete-then-insert that would trip the (order, variant) unique constraint.
        entity.setId(lineId(orderId, line.variantId()));
        entity.setProductId(line.productId());
        entity.setVariantId(line.variantId());
        entity.setProductName(line.productName());
        entity.setVariantLabel(line.variantLabel());
        entity.setSku(line.sku());
        entity.setUnitPriceMinorUnits(line.unitPrice().minorUnits());
        entity.setCurrency(line.unitPrice().currency().getCurrencyCode());
        entity.setQuantity(line.quantity().value());
        entity.setDisplayOrder(displayOrder);
        return entity;
    }

    private static OrderLine toDomain(OrderLineJpaEntity entity) {
        return new OrderLine(entity.getProductId(), entity.getVariantId(), entity.getProductName(),
                entity.getVariantLabel(), entity.getSku(),
                Money.ofMinorUnits(entity.getUnitPriceMinorUnits(),
                        java.util.Currency.getInstance(entity.getCurrency())),
                Quantity.of(entity.getQuantity()));
    }

    private static OrderStatusHistoryJpaEntity toEntity(OrderJpaEntity order, StatusChange change,
                                                        int displayOrder) {
        OrderStatusHistoryJpaEntity entity = new OrderStatusHistoryJpaEntity();
        entity.setId(change.id());
        entity.setOrder(order);
        entity.setFromStatus(change.fromStatus() == null ? null : change.fromStatus().name());
        entity.setToStatus(change.toStatus().name());
        entity.setReason(change.reason());
        entity.setActorId(change.actorId());
        entity.setOccurredAt(change.occurredAt());
        entity.setDisplayOrder(displayOrder);
        return entity;
    }

    private static StatusChange toDomain(OrderStatusHistoryJpaEntity entity) {
        return new StatusChange(entity.getId(),
                entity.getFromStatus() == null ? null : OrderStatus.valueOf(entity.getFromStatus()),
                OrderStatus.valueOf(entity.getToStatus()), entity.getReason(), entity.getActorId(),
                entity.getOccurredAt());
    }

    private static OrderNoteJpaEntity toEntity(OrderJpaEntity order, OrderNote note) {
        OrderNoteJpaEntity entity = new OrderNoteJpaEntity();
        entity.setId(note.id());
        entity.setOrder(order);
        entity.setText(note.text());
        entity.setAuthorId(note.authorId());
        entity.setCreatedAt(note.createdAt());
        return entity;
    }

    private static OrderNote toDomain(OrderNoteJpaEntity entity) {
        return new OrderNote(entity.getId(), entity.getText(), entity.getAuthorId(),
                entity.getCreatedAt());
    }

    static UUID lineId(UUID orderId, UUID variantId) {
        return UUID.nameUUIDFromBytes((orderId + "|" + variantId).getBytes(StandardCharsets.UTF_8));
    }

    // ---------------------------------------------------------------------------------------------
    // Cart
    // ---------------------------------------------------------------------------------------------

    static CartJpaEntity toEntity(Cart cart) {
        CartJpaEntity entity = new CartJpaEntity();
        entity.setId(cart.id());
        entity.setCustomerId(cart.customerId());
        entity.setCreatedAt(cart.createdAt());
        entity.setUpdatedAt(cart.updatedAt());

        List<CartLineJpaEntity> lines = new ArrayList<>();
        List<CartLine> domainLines = cart.lines();
        for (int i = 0; i < domainLines.size(); i++) {
            lines.add(toEntity(cart.id(), domainLines.get(i), i));
        }
        entity.setLines(lines);
        return entity;
    }

    static Cart toDomain(CartJpaEntity entity) {
        Cart cart = Cart.rehydrate(entity.getId(), entity.getCustomerId(), entity.getCreatedAt(),
                entity.getUpdatedAt());
        cart.attachLines(entity.getLines().stream().map(OrderPersistenceMapper::toDomain).toList());
        return cart;
    }

    private static CartLineJpaEntity toEntity(UUID cartId, CartLine line, int displayOrder) {
        CartLineJpaEntity entity = new CartLineJpaEntity();
        entity.setId(lineId(cartId, line.variantId()));
        entity.setProductId(line.productId());
        entity.setVariantId(line.variantId());
        entity.setProductName(line.productName());
        entity.setVariantLabel(line.variantLabel());
        entity.setUnitPriceMinorUnits(line.unitPrice().minorUnits());
        entity.setCurrency(line.unitPrice().currency().getCurrencyCode());
        entity.setQuantity(line.quantity().value());
        entity.setDisplayOrder(displayOrder);
        return entity;
    }

    private static CartLine toDomain(CartLineJpaEntity entity) {
        return new CartLine(entity.getProductId(), entity.getVariantId(), entity.getProductName(),
                entity.getVariantLabel(),
                Money.ofMinorUnits(entity.getUnitPriceMinorUnits(),
                        java.util.Currency.getInstance(entity.getCurrency())),
                Quantity.of(entity.getQuantity()));
    }

    // ---------------------------------------------------------------------------------------------
    // Idempotency
    // ---------------------------------------------------------------------------------------------

    static IdempotencyRecordJpaEntity toEntity(IdempotencyRecord record) {
        IdempotencyRecordJpaEntity entity = new IdempotencyRecordJpaEntity();
        entity.setId(record.id());
        entity.setScope(record.scope());
        entity.setKey(record.key());
        entity.setRequestFingerprint(record.requestFingerprint());
        entity.setResourceId(record.resourceId());
        entity.setResponseBody(record.responseBody());
        entity.setResponseStatus(record.responseStatus());
        entity.setCreatedAt(record.createdAt());
        entity.setExpiresAt(record.expiresAt());
        return entity;
    }

    static IdempotencyRecord toDomain(IdempotencyRecordJpaEntity entity) {
        return new IdempotencyRecord(entity.getId(), entity.getScope(), entity.getKey(),
                entity.getRequestFingerprint(), entity.getResourceId(), entity.getResponseBody(),
                entity.getResponseStatus() == null ? 0 : entity.getResponseStatus(),
                entity.getCreatedAt(), entity.getExpiresAt());
    }
}
