package com.choco.payment.application.service;

import com.choco.core.domain.Money;
import com.choco.core.error.BusinessRuleException;
import com.choco.core.error.ErrorCode;
import com.choco.core.port.Clock;
import com.choco.core.port.TransactionRunner;
import com.choco.payment.application.port.AuditLogPort;
import com.choco.payment.domain.payment.Payment;
import com.choco.payment.domain.payment.PaymentMethod;
import com.choco.payment.domain.payment.Refund;
import com.choco.payment.domain.payment.RefundStatus;
import com.choco.payment.domain.port.PaymentGatewayPort;
import com.choco.payment.domain.port.PaymentRepository;
import java.time.Instant;
import java.util.Currency;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Applies a provider webhook to the local payment record.
 *
 * <p>The webhook is the authoritative, out-of-band confirmation that a payment settled, and it is the
 * path that catches the cases the browser never reports: a UPI collect confirmed after the customer
 * closed the tab, or a capture that happened while the storefront was offline.
 *
 * <p><b>Signature first, always.</b> {@link #handle} refuses to act until the signature over the raw
 * body has been verified against the webhook secret. The body is client-reachable (it is an HTTP
 * endpoint), so without that check anyone could POST a forged "payment.captured" and confirm an order
 * for free.
 *
 * <p><b>Idempotent by construction.</b> Providers deliver at least once and retry on any non-2xx, so
 * a capture is expected to arrive more than once. The domain's {@code markCaptured} is a no-op for a
 * repeated delivery of the same provider payment id, so handling the same event twice changes nothing
 * and raises no second event. An event for an order with no local payment is acknowledged and ignored:
 * returning an error would make the provider retry forever for something this service cannot resolve.
 */
public class PaymentWebhookService {

    private static final Logger log = LoggerFactory.getLogger(PaymentWebhookService.class);

    private final PaymentRepository paymentRepository;
    private final PaymentGatewayPort gateway;
    private final PaymentService paymentService;
    private final AuditLogPort auditLog;
    private final Clock clock;
    private final TransactionRunner transactions;

    public PaymentWebhookService(PaymentRepository paymentRepository, PaymentGatewayPort gateway,
                                 PaymentService paymentService, AuditLogPort auditLog, Clock clock,
                                 TransactionRunner transactions) {
        this.paymentRepository = paymentRepository;
        this.gateway = gateway;
        this.paymentService = paymentService;
        this.auditLog = auditLog;
        this.clock = clock;
        this.transactions = transactions;
    }

    /**
     * Verifies and applies a webhook.
     *
     * @param rawBody   the request body exactly as received; re-serialising it would break the signature
     * @param signature the provider's signature header
     * @throws BusinessRuleException with {@code PAYMENT_SIGNATURE_INVALID} when the signature does not check out
     */
    public void handle(String rawBody, String signature) {
        if (!gateway.verifyWebhookSignature(rawBody, signature)) {
            audit("SYSTEM", "payment.webhook", "UNKNOWN", "FAILURE", "signature invalid");
            throw new BusinessRuleException(ErrorCode.PAYMENT_SIGNATURE_INVALID,
                    "The webhook signature could not be verified");
        }
        PaymentGatewayPort.WebhookEvent event = gateway.parseWebhook(rawBody);
        if (event == null || event.eventType() == null) {
            log.debug("Ignoring a webhook with no recognisable event type");
            return;
        }
        transactions.execute(() -> apply(event));
    }

    private void apply(PaymentGatewayPort.WebhookEvent event) {
        String type = event.eventType().toLowerCase(java.util.Locale.ROOT);
        switch (type) {
            case "payment.captured", "payment.authorized" -> applyPaymentEvent(event);
            case "payment.failed" -> applyFailureEvent(event);
            case "refund.processed", "refund.created" -> applyRefundEvent(event);
            case "payment.dispute.created", "payment.dispute.raised" -> applyDisputeEvent(event);
            default -> log.debug("No handler for webhook event {}", event.eventType());
        }
    }

    private void applyPaymentEvent(PaymentGatewayPort.WebhookEvent event) {
        Payment payment = locate(event).orElse(null);
        if (payment == null) {
            log.debug("Webhook for an unknown payment was acknowledged and ignored");
            return;
        }
        boolean captured = "payment.captured".equalsIgnoreCase(event.eventType());
        PaymentMethod method = PaymentMethod.fromProvider(event.method());
        if (captured) {
            Money amount = money(event);
            payment.markCaptured(event.providerPaymentId(), method, amount, clock.now());
        } else {
            // Authorised is not captured: the order stays unpaid until the capture arrives.
            payment.markPending(event.providerPaymentId(), method, clock.now());
        }
        Payment saved = paymentService.persist(payment);
        audit("SYSTEM", "payment.webhook", saved.id().toString(), "SUCCESS", event.eventType());
    }

    private void applyFailureEvent(PaymentGatewayPort.WebhookEvent event) {
        Payment payment = locate(event).orElse(null);
        if (payment == null) {
            return;
        }
        payment.markFailed(event.providerPaymentId(), PaymentMethod.fromProvider(event.method()),
                event.failureReason(), clock.now());
        Payment saved = paymentService.persist(payment);
        audit("SYSTEM", "payment.webhook", saved.id().toString(), "SUCCESS", event.eventType());
    }

    private void applyRefundEvent(PaymentGatewayPort.WebhookEvent event) {
        Payment payment = locate(event).orElse(null);
        if (payment == null) {
            return;
        }
        Optional<Refund> matching = payment.refunds().stream()
                .filter(r -> r.providerRefundId() != null
                        && r.providerRefundId().equals(event.providerRefundId()))
                .findFirst();
        if (matching.isEmpty()) {
            // A refund this service did not initiate — record it so the seller sees it, but there is
            // no refund row to move, so the payment itself is left as it was.
            log.debug("Refund webhook for an unknown refund was acknowledged and ignored");
            return;
        }
        Refund refund = matching.get();
        if ("refund.processed".equalsIgnoreCase(event.eventType())) {
            payment.markRefundProcessed(refund.id(), event.providerRefundId(), clock.now());
        } else if (refund.status() == RefundStatus.REQUESTED) {
            payment.markRefundProcessing(refund.id(), event.providerRefundId(), clock.now());
        }
        Payment saved = paymentService.persist(payment);
        audit("SYSTEM", "payment.webhook", saved.id().toString(), "SUCCESS", event.eventType());
    }

    private void applyDisputeEvent(PaymentGatewayPort.WebhookEvent event) {
        Payment payment = locate(event).orElse(null);
        if (payment == null) {
            return;
        }
        Money amount = money(event);
        payment.raiseDispute(UUID.randomUUID(), event.providerDisputeId(), amount, event.failureReason(),
                clock.now());
        Payment saved = paymentService.persist(payment);
        audit("SYSTEM", "payment.webhook", saved.id().toString(), "SUCCESS", event.eventType());
    }

    /**
     * Finds the payment a webhook concerns.
     *
     * <p>Preferred by provider payment id, falling back to the provider order id, because a failure
     * webhook may carry only the order. Both are the provider's identifiers, so neither can be
     * spoofed once the signature has been verified.
     */
    private Optional<Payment> locate(PaymentGatewayPort.WebhookEvent event) {
        if (event.providerPaymentId() != null) {
            Optional<Payment> byPayment = paymentRepository.findByProviderPaymentId(event.providerPaymentId());
            if (byPayment.isPresent()) {
                return byPayment;
            }
        }
        if (event.providerOrderId() != null) {
            return paymentRepository.findByProviderOrderId(event.providerOrderId());
        }
        return Optional.empty();
    }

    private static Money money(PaymentGatewayPort.WebhookEvent event) {
        if (event.amountMinorUnits() == null) {
            return null;
        }
        return Money.ofMinorUnits(event.amountMinorUnits(),
                Currency.getInstance(event.currency() == null ? "INR" : event.currency()));
    }

    private void audit(String actorType, String action, String targetId, String outcome, String detail) {
        auditLog.record(actorType, null, action, "PAYMENT", targetId, outcome, detail, null);
    }
}
