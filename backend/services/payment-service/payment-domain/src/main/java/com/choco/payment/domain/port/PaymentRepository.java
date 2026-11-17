package com.choco.payment.domain.port;

import com.choco.core.page.PageResult;
import com.choco.core.port.PageQuery;
import com.choco.payment.domain.payment.Payment;
import com.choco.payment.domain.payment.PaymentStatus;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence port for payments.
 *
 * <p>Reads are scoped by customer wherever a customer can trigger them, so a guessed payment id
 * belonging to someone else cannot be read. There is deliberately no unscoped {@code findById} used by
 * a customer-facing path.
 */
public interface PaymentRepository {

    Payment save(Payment payment);

    Optional<Payment> findById(UUID id);

    /** Loads a payment scoped to its owner; a cross-customer id returns empty rather than 403. */
    Optional<Payment> findByIdAndCustomerId(UUID id, UUID customerId);

    Optional<Payment> findByOrderId(UUID orderId);

    /** The lookup for a webhook, which carries the provider's own identifiers and nothing else. */
    Optional<Payment> findByProviderOrderId(String providerOrderId);

    /** The idempotent lookup for a repeated webhook or verification carrying the same payment id. */
    Optional<Payment> findByProviderPaymentId(String providerPaymentId);

    PageResult<Payment> findByCustomerId(UUID customerId, PageQuery page);

    PageResult<Payment> search(Collection<PaymentStatus> statuses, String search, PageQuery page);

    long count();

    long countByStatus(PaymentStatus status);
}
