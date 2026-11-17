package com.choco.payment.application.service;

import com.choco.core.domain.Money;
import com.choco.core.error.BusinessRuleException;
import com.choco.core.error.ErrorCode;
import com.choco.core.error.ForbiddenException;
import com.choco.core.error.IdempotencyConflictException;
import com.choco.core.error.NotFoundException;
import com.choco.core.error.ValidationException;
import com.choco.core.port.Clock;
import com.choco.core.port.EventPublisher;
import com.choco.core.port.IdGenerator;
import com.choco.core.port.TransactionRunner;
import com.choco.payment.application.command.InitiatePaymentCommand;
import com.choco.payment.application.command.VerifyPaymentCommand;
import com.choco.payment.application.dto.PaymentInitiationView;
import com.choco.payment.application.dto.PaymentView;
import com.choco.payment.application.port.AuditLogPort;
import com.choco.payment.domain.idempotency.IdempotencyRecord;
import com.choco.payment.domain.payment.Payment;
import com.choco.payment.domain.payment.PaymentMethod;
import com.choco.payment.domain.payment.PaymentStatus;
import com.choco.payment.domain.port.IdempotencyRecordRepository;
import com.choco.payment.domain.port.PaymentGatewayPort;
import com.choco.payment.domain.port.PaymentRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.Currency;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Starts a payment and verifies its outcome.
 *
 * <p>Two entry points that together implement the rule this service exists to enforce: a payment is
 * only ever recorded as successful after the <em>server</em> has confirmed it with the provider.
 *
 * <p>{@link #initiate} creates a provider order and a local payment in {@code CREATED}. It is
 * idempotent per order and per idempotency key: the order service retries this call after a timeout,
 * and a second provider order for the same customer order would be a real problem.
 *
 * <p>{@link #verify} is the callback the storefront makes after the provider's checkout. It checks the
 * checkout signature, then fetches the payment from the provider and inspects the authoritative state.
 * A captured payment is recorded; anything else is recorded as pending or failed. The browser's word
 * alone is never enough — a redirect to a success page proves nothing, and a forged one proves less.
 */
public class PaymentService {

    private static final String SCOPE = "payment-initiate";
    private static final Duration IDEMPOTENCY_TTL = Duration.ofHours(24);
    private static final String PROVIDER = "razorpay";

    private final PaymentRepository paymentRepository;
    private final IdempotencyRecordRepository idempotencyRepository;
    private final PaymentGatewayPort gateway;
    private final EventPublisher eventPublisher;
    private final AuditLogPort auditLog;
    private final Clock clock;
    private final IdGenerator idGenerator;
    private final TransactionRunner transactions;

    public PaymentService(PaymentRepository paymentRepository,
                          IdempotencyRecordRepository idempotencyRepository,
                          PaymentGatewayPort gateway, EventPublisher eventPublisher, AuditLogPort auditLog,
                          Clock clock, IdGenerator idGenerator, TransactionRunner transactions) {
        this.paymentRepository = paymentRepository;
        this.idempotencyRepository = idempotencyRepository;
        this.gateway = gateway;
        this.eventPublisher = eventPublisher;
        this.auditLog = auditLog;
        this.clock = clock;
        this.idGenerator = idGenerator;
        this.transactions = transactions;
    }

    /**
     * Creates a provider order and a local payment for an order.
     *
     * <p>If a payment already exists for the order it is returned as-is rather than a second provider
     * order being created: the order service retrying this call must not cause a double charge. An
     * idempotency key, when supplied, is honoured the same way and its stored fingerprint refuses a
     * replay for a different order.
     */
    public PaymentInitiationView initiate(InitiatePaymentCommand command) {
        requireCommand(command);
        String key = normaliseKey(command.idempotencyKey());
        return transactions.execute(() -> {
            Optional<Payment> existing = paymentRepository.findByOrderId(command.orderId());
            if (existing.isPresent()) {
                return toInitiationView(existing.get());
            }
            if (key != null) {
                var replay = idempotencyRepository.findByScopeAndKey(SCOPE, key)
                        .filter(record -> !record.isExpiredAt(clock.now()));
                if (replay.isPresent()) {
                    IdempotencyRecord record = replay.get();
                    if (record.requestFingerprint() != null
                            && !record.requestFingerprint().equals(command.orderId().toString())) {
                        throw new IdempotencyConflictException(
                                "This idempotency key was already used for a different order");
                    }
                    Payment recorded = paymentRepository.findById(UUID.fromString(record.resourceId()))
                            .orElseThrow(() -> new NotFoundException(ErrorCode.NOT_FOUND,
                                    "The payment for this request could not be found"));
                    return toInitiationView(recorded);
                }
            }

            Money amount = Money.ofMinorUnits(command.amountMinorUnits(),
                    Currency.getInstance(command.currency() == null ? "INR" : command.currency()));
            PaymentGatewayPort.ProviderOrder providerOrder = gateway.createOrder(amount, command.receipt(),
                    Map.of("orderId", command.orderId().toString()));

            Instant now = clock.now();
            Payment payment = Payment.open(idGenerator.nextId(), command.orderId(), command.customerId(),
                    amount, command.receipt(), PROVIDER, providerOrder.id(), now);
            Payment saved = paymentRepository.save(payment);

            if (key != null) {
                idempotencyRepository.save(IdempotencyRecord.of(idGenerator.nextId(), SCOPE, key,
                        command.orderId().toString(), saved.id().toString(), saved.id().toString(), 201,
                        now, now.plus(IDEMPOTENCY_TTL)));
            }
            audit("SYSTEM", null, "payment.initiate", saved, "SUCCESS", providerOrder.id());
            return toInitiationView(saved);
        });
    }

    /**
     * Verifies the provider's checkout response and records the true outcome.
     *
     * <p>Order of operations matters and is deliberate:
     * <ol>
     *   <li>the signature is checked first, so a forged response is rejected before it can touch state;
     *   <li>the payment is then fetched from the provider, because a valid signature proves the
     *       response came from the provider, not that the money was captured;
     *   <li>only a captured payment, for the expected amount, is recorded as paid.
     * </ol>
     * A failure to reach the provider leaves the payment pending rather than failed: the customer may
     * have paid, and a webhook will settle the truth. Marking it failed on a network blip would tell a
     * paying customer their payment did not go through.
     */
    public PaymentView verify(UUID customerId, VerifyPaymentCommand command) {
        if (command.providerPaymentId() == null || command.providerPaymentId().isBlank()) {
            throw new ValidationException("A provider payment id is required");
        }
        return transactions.execute(() -> {
            Payment payment = paymentRepository.findByOrderId(command.orderId())
                    .orElseThrow(() -> new NotFoundException(ErrorCode.NOT_FOUND,
                            "No payment was started for this order"));
            if (!payment.customerId().equals(customerId)) {
                throw new ForbiddenException("You are not allowed to verify this payment");
            }
            if (payment.isPaid()) {
                // Already settled — a duplicate verification, most likely a double-submitted callback.
                return PaymentViewMapper.toView(payment);
            }
            if (!gateway.verifyCheckoutSignature(command.providerOrderId(), command.providerPaymentId(),
                    command.signature())) {
                payment.markFailed(command.providerPaymentId(), null, "Signature verification failed",
                        clock.now());
                Payment saved = persist(payment);
                audit("CUSTOMER", customerId.toString(), "payment.verify", saved, "FAILURE",
                        "signature invalid");
                throw new BusinessRuleException(ErrorCode.PAYMENT_SIGNATURE_INVALID,
                        "The payment could not be verified");
            }

            PaymentGatewayPort.ProviderPayment reported;
            try {
                reported = gateway.fetchPayment(command.providerPaymentId());
            } catch (BusinessRuleException ex) {
                // Provider unreachable: record the attempt as pending and let the webhook settle it.
                payment.markPending(command.providerPaymentId(), null, clock.now());
                persist(payment);
                throw new BusinessRuleException(ErrorCode.DEPENDENCY_UNAVAILABLE,
                        "The payment status could not be confirmed yet; it will update shortly");
            }
            applyReportedState(payment, reported);
            Payment saved = persist(payment);
            audit("CUSTOMER", customerId.toString(), "payment.verify", saved,
                    saved.isPaid() ? "SUCCESS" : "PENDING", reported.status());
            return PaymentViewMapper.toView(saved);
        });
    }

    /**
     * Applies a provider-reported payment state to the aggregate.
     *
     * <p>Shared by verification and the webhook so the two cannot drift: whichever path observes a
     * capture, the same rule — captured, for the right amount — decides it is paid.
     */
    void applyReportedState(Payment payment, PaymentGatewayPort.ProviderPayment reported) {
        PaymentMethod method = PaymentMethod.fromProvider(reported.method());
        if (reported.captured()) {
            Money captured = Money.ofMinorUnits(reported.amountMinorUnits(),
                    Currency.getInstance(reported.currency() == null ? "INR" : reported.currency()));
            payment.markCaptured(reported.id(), method, captured, clock.now());
        } else if (isFailure(reported.status())) {
            payment.markFailed(reported.id(), method, reported.failureReason(), clock.now());
        } else {
            payment.markPending(reported.id(), method, clock.now());
        }
    }

    private static boolean isFailure(String status) {
        if (status == null) {
            return false;
        }
        return switch (status.toLowerCase(java.util.Locale.ROOT)) {
            case "failed", "cancelled", "canceled", "expired", "declined" -> true;
            default -> false;
        };
    }

    /** Saves the aggregate and publishes everything it recorded, after the save. */
    Payment persist(Payment payment) {
        Payment saved = paymentRepository.save(payment);
        eventPublisher.publishAll(payment.pullDomainEvents());
        return saved;
    }

    PaymentInitiationView toInitiationView(Payment payment) {
        return new PaymentInitiationView(
                payment.provider(),
                payment.providerOrderId(),
                payment.amount().minorUnits(),
                PaymentViewMapper.decimal(payment.amount()),
                payment.amount().currency().getCurrencyCode(),
                payment.receipt(),
                gateway.publishableKey());
    }

    private void requireCommand(InitiatePaymentCommand command) {
        if (command.orderId() == null) {
            throw new ValidationException("An order id is required to start a payment");
        }
        if (command.customerId() == null) {
            throw new ValidationException("A customer id is required to start a payment");
        }
        if (command.amountMinorUnits() <= 0) {
            throw new ValidationException("A payment amount must be greater than zero");
        }
    }

    private static String normaliseKey(String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        String trimmed = key.trim();
        if (trimmed.length() > 200) {
            throw new ValidationException("The idempotency key is too long");
        }
        return trimmed;
    }

    private void audit(String actorType, String actorId, String action, Payment payment, String outcome,
                       String detail) {
        auditLog.record(actorType, actorId, action, "PAYMENT", payment.id().toString(), outcome, detail,
                null);
    }
}
