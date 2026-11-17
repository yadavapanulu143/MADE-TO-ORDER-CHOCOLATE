package com.choco.events.envelope;

/**
 * Canonical event type names and routing keys.
 *
 * <p>Consumers bind to these constants, so a producer cannot silently rename an event and break
 * everyone downstream without the change appearing in a shared file.
 */
public final class EventTypes {

    // identity-service
    public static final String CUSTOMER_REGISTERED = "customer.registered";
    public static final String CUSTOMER_VERIFIED = "customer.verified";
    public static final String CUSTOMER_PASSWORD_CHANGED = "customer.password-changed";
    public static final String CUSTOMER_CONTACT_CHANGED = "customer.contact-changed";

    // catalog-service
    public static final String PRODUCT_CREATED = "catalog.product-created";
    public static final String PRODUCT_UPDATED = "catalog.product-updated";
    public static final String VARIANT_PRICE_CHANGED = "catalog.variant-price-changed";

    // order-service
    public static final String ORDER_PLACED = "order.placed";
    public static final String ORDER_CONFIRMED = "order.confirmed";
    public static final String ORDER_CANCELLED = "order.cancelled";
    public static final String REFUND_REQUESTED = "order.refund-requested";
    public static final String INVOICE_ISSUED = "order.invoice-issued";

    // payment-service
    public static final String PAYMENT_CAPTURED = "payment.captured";
    public static final String PAYMENT_FAILED = "payment.failed";
    public static final String PAYMENT_PENDING = "payment.pending";
    public static final String REFUND_PROCESSED = "payment.refund-processed";
    public static final String DISPUTE_RAISED = "payment.dispute-raised";

    // fulfilment-service
    public static final String PRODUCTION_STARTED = "fulfilment.production-started";
    public static final String PRODUCTION_COMPLETED = "fulfilment.production-completed";
    public static final String READY_FOR_DISPATCH = "fulfilment.ready-for-dispatch";
    public static final String DELIVERY_ASSIGNED = "fulfilment.delivery-assigned";
    public static final String OUT_FOR_DELIVERY = "fulfilment.out-for-delivery";
    public static final String DELIVERY_DELIVERED = "fulfilment.delivery-delivered";
    public static final String DELIVERY_FAILED = "fulfilment.delivery-failed";
    public static final String LOW_STOCK_DETECTED = "fulfilment.low-stock";

    // notification-service
    public static final String NOTIFICATION_SENT = "notification.sent";
    public static final String NOTIFICATION_FAILED = "notification.failed";

    private EventTypes() {
    }
}
