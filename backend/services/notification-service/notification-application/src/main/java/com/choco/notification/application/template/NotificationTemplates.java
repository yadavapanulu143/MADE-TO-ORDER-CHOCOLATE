package com.choco.notification.application.template;

import com.choco.notification.domain.notification.NotificationChannel;
import com.choco.notification.domain.notification.NotificationType;
import java.util.Map;
import java.util.EnumMap;

/**
 * The message text for every notification type, kept in one place.
 *
 * <p>Templates live in code rather than in the database. That is a deliberate trade-off for a
 * single-seller business: an in-code template is reviewed, versioned and diffed like the rest of the
 * system, and a wording change cannot be made by someone with a database console and no review. The
 * cost is that changing a sentence is a deployment; for a catalogue this small that cost is small.
 *
 * <p>Each entry produces both a plain-text and an HTML body for email, and a single text body for SMS.
 * The text body is the source of truth — every message is readable with no formatting — and the HTML
 * body is the same content in a small, fixed wrapper. Nothing here interpolates a value into HTML
 * without escaping; {@link MessageContent#render} does the escaping.
 *
 * <p>The wrapper is intentionally plain and inline-styled: email clients strip external stylesheets
 * and block images, so a template that depended on either would render broken for many recipients.
 */
public final class NotificationTemplates {

    /** Brand name used in the greeting and sign-off. */
    private static final String BRAND = "Choco";

    private static final Map<NotificationType, Template> TEMPLATES = new EnumMap<>(NotificationType.class);

    private record Template(String subject, String text) {
    }

    static {
        // --- Order -----------------------------------------------------------------------------
        put(NotificationType.ORDER_PLACED,
                "We received your order {{orderNumber}}",
                """
                Hi {{customerName}},

                Thank you. We have received your order {{orderNumber}} for a total of {{totalAmount}}.

                Your chocolates are made to order. We will begin preparing them as soon as your
                payment is confirmed.
                """);

        put(NotificationType.ORDER_CONFIRMED,
                "Your order {{orderNumber}} is confirmed",
                """
                Hi {{customerName}},

                Your payment for order {{orderNumber}} has been confirmed. We have started to prepare
                your made-to-order chocolates.

                We will let you know as your order moves through preparation and delivery.
                """);

        put(NotificationType.ORDER_CANCELLED,
                "Your order {{orderNumber}} was cancelled",
                """
                Hi {{customerName}},

                Your order {{orderNumber}} has been cancelled.

                Reason: {{reason}}

                If you did not expect this, please contact us.
                """);

        put(NotificationType.INVOICE_ISSUED,
                "Your invoice {{invoiceNumber}} for order {{orderNumber}}",
                """
                Hi {{customerName}},

                Your invoice {{invoiceNumber}} for order {{orderNumber}} is ready.

                Total billed: {{amount}}

                You can download the invoice from your account at any time.
                """);

        // --- Payment ---------------------------------------------------------------------------
        put(NotificationType.PAYMENT_SUCCESS,
                "Payment received for order {{orderNumber}}",
                """
                Hi {{customerName}},

                We have received your payment of {{amount}} for order {{orderNumber}}.

                Your order is now confirmed and will move into preparation.
                """);

        put(NotificationType.PAYMENT_FAILED,
                "Payment could not be completed for order {{orderNumber}}",
                """
                Hi {{customerName}},

                Your payment of {{amount}} for order {{orderNumber}} did not go through.

                Reason: {{reason}}

                No money has been taken. You can try again from your account.
                """);

        put(NotificationType.PAYMENT_PENDING,
                "We are waiting for your payment for order {{orderNumber}}",
                """
                Hi {{customerName}},

                Your payment of {{amount}} for order {{orderNumber}} is still being processed.

                We will confirm as soon as it completes. If it does not, the order will not be
                prepared.
                """);

        // --- Refunds and disputes --------------------------------------------------------------
        put(NotificationType.REFUND_REQUESTED,
                "Refund request for order {{orderNumber}}",
                """
                A customer has requested a refund.

                Order: {{orderNumber}}
                Amount: {{amount}}
                Reason: {{reason}}

                Please review this request in the admin dashboard.
                """);

        put(NotificationType.REFUND_PROCESSED,
                "Refund processed for order {{orderNumber}}",
                """
                Hi {{customerName}},

                We have processed a refund of {{amount}} for order {{orderNumber}}.

                The amount will be credited to your original payment method. The time it takes to
                appear depends on your bank or payment provider.
                """);

        put(NotificationType.DISPUTE_RAISED,
                "Payment dispute raised for order {{orderNumber}}",
                """
                A payment dispute has been raised.

                Order: {{orderNumber}}
                Amount: {{amount}}
                Reason: {{reason}}

                A dispute usually has a deadline to respond. Please review it in the admin dashboard.
                """);

        // --- Production ------------------------------------------------------------------------
        put(NotificationType.PRODUCTION_STARTED,
                "We have started preparing order {{orderNumber}}",
                """
                Hi {{customerName}},

                Good news — we have started preparing your made-to-order chocolates for order
                {{orderNumber}}.

                We will let you know when preparation is complete.
                """);

        put(NotificationType.PRODUCTION_COMPLETED,
                "Your chocolates for order {{orderNumber}} are ready",
                """
                Hi {{customerName}},

                Your chocolates for order {{orderNumber}} have finished preparation. We are now
                packing them for dispatch.
                """);

        put(NotificationType.READY_FOR_DISPATCH,
                "Order {{orderNumber}} is ready for dispatch",
                """
                Hi {{customerName}},

                Your order {{orderNumber}} has been packed and is ready for dispatch. We will assign it for
                delivery shortly.
                """);

        // --- Delivery --------------------------------------------------------------------------
        put(NotificationType.DELIVERY_ASSIGNED,
                "Delivery assigned for order {{orderNumber}}",
                """
                Hi {{customerName}},

                A delivery has been arranged for your order {{orderNumber}}. We will let you know when
                it is out for delivery.
                """);

        put(NotificationType.OUT_FOR_DELIVERY,
                "Your order {{orderNumber}} is out for delivery",
                """
                %s: Your order {{orderNumber}} is out for delivery today. Please keep your phone
                available.
                """.formatted(BRAND));

        put(NotificationType.DELIVERY_DELIVERED,
                "Order {{orderNumber}} delivered",
                """
                %s: Your order {{orderNumber}} has been delivered. Thank you for your order.
                """.formatted(BRAND));

        put(NotificationType.DELIVERY_FAILED,
                "Delivery attempted for order {{orderNumber}}",
                """
                Hi {{customerName}},

                We tried to deliver order {{orderNumber}} but could not complete the delivery.

                Reason: {{reason}}

                We will arrange another attempt. If you would like to change the address or a delivery
                detail, please contact us.
                """);

        // --- Operations (seller) ---------------------------------------------------------------
        put(NotificationType.LOW_STOCK_ALERT,
                "Low stock: {{ingredientName}}",
                """
                Stock is running low.

                Ingredient: {{ingredientName}}
                On hand: {{onHand}}
                Minimum: {{minimum}}

                Please restock before accepting orders that need this ingredient.
                """);
    }

    private NotificationTemplates() {
    }

    private static void put(NotificationType type, String subject, String text) {
        TEMPLATES.put(type, new Template(subject, text));
    }

    /**
     * Renders the message for a type.
     *
     * <p>Fails loudly for an unknown type: every {@link NotificationType} must have a template, and a
     * type added without one should break a test, not send a customer an empty message.
     *
     * <p>Only the subject and the plain-text body are produced here. The HTML part is not stored with
     * the notification; it is derived at send time by {@link #wrapHtml} from the same text, so a retry
     * can never send an HTML body that disagrees with the text the record holds.
     */
    public static MessageContent render(NotificationType type, Map<String, String> variables) {
        Template template = TEMPLATES.get(type);
        if (template == null) {
            throw new IllegalStateException("No template is defined for " + type);
        }
        return MessageContent.render(template.subject(), template.text(), null, variables);
    }

    /**
     * Wraps a plain-text body in the HTML shell, escaping it.
     *
     * <p>The body is HTML-escaped, so a customer's display name or a delivery note interpolated into
     * the text cannot introduce a tag or a script into the HTML part.
     */
    public static String wrapHtml(String textBody) {
        return MessageContent.substitute(HTML_WRAPPER, Map.of("body", textBody), true);
    }

    public static boolean hasTemplate(NotificationType type) {
        return TEMPLATES.containsKey(type);
    }

    /**
     * Minimal HTML shell. The body is HTML-escaped and its newlines preserved, so the HTML part carries
     * exactly the information the text part does and cannot render a tag a template did not intend.
     *
     * <p>The styling is deliberately plain and inline: email clients strip external stylesheets and
     * block images, so anything richer would render broken for many recipients.
     */
    private static final String HTML_WRAPPER = """
            <!DOCTYPE html>
            <html>
            <body style="font-family: Arial, Helvetica, sans-serif; color: #2b2b2b; line-height: 1.5;">
              <div style="max-width: 560px; margin: 0 auto; padding: 24px;">
                <p style="white-space: pre-wrap; margin: 0;">{{body}}</p>
                <hr style="border: none; border-top: 1px solid #eee; margin: 24px 0;">
                <p style="font-size: 12px; color: #888; margin: 0;">%s &mdash; made-to-order chocolate.</p>
              </div>
            </body>
            </html>
            """.formatted(BRAND);
}
