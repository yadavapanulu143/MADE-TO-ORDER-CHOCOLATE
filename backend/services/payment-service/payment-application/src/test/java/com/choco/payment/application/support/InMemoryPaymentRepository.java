package com.choco.payment.application.support;

import com.choco.core.page.PageResult;
import com.choco.core.port.PageQuery;
import com.choco.payment.domain.payment.Payment;
import com.choco.payment.domain.payment.PaymentStatus;
import com.choco.payment.domain.port.PaymentRepository;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * In-memory {@link PaymentRepository} for use-case tests.
 *
 * <p>A real map-backed store rather than a Mockito stub: the use cases read back what they just wrote
 * — the initiation path looks a payment up by order id immediately after saving it — so a stub that
 * only recorded calls would not exercise the path at all.
 */
public final class InMemoryPaymentRepository implements PaymentRepository {

    private final Map<UUID, Payment> byId = new LinkedHashMap<>();

    @Override
    public Payment save(Payment payment) {
        byId.put(payment.id(), payment);
        return payment;
    }

    @Override
    public Optional<Payment> findById(UUID id) {
        return Optional.ofNullable(byId.get(id));
    }

    @Override
    public Optional<Payment> findByIdAndCustomerId(UUID id, UUID customerId) {
        return findById(id).filter(p -> p.customerId().equals(customerId));
    }

    @Override
    public Optional<Payment> findByOrderId(UUID orderId) {
        return byId.values().stream().filter(p -> p.orderId().equals(orderId)).findFirst();
    }

    @Override
    public Optional<Payment> findByProviderOrderId(String providerOrderId) {
        return byId.values().stream()
                .filter(p -> providerOrderId.equals(p.providerOrderId()))
                .findFirst();
    }

    @Override
    public Optional<Payment> findByProviderPaymentId(String providerPaymentId) {
        return byId.values().stream()
                .filter(p -> providerPaymentId.equals(p.providerPaymentId()))
                .findFirst();
    }

    @Override
    public PageResult<Payment> findByCustomerId(UUID customerId, PageQuery page) {
        List<Payment> matches = byId.values().stream()
                .filter(p -> p.customerId().equals(customerId))
                .sorted(Comparator.comparing(Payment::createdAt).reversed())
                .toList();
        return pageOf(matches, page);
    }

    @Override
    public PageResult<Payment> search(Collection<PaymentStatus> statuses, String search, PageQuery page) {
        String needle = search == null ? null : search.toLowerCase(java.util.Locale.ROOT);
        List<Payment> matches = byId.values().stream()
                .filter(p -> statuses == null || statuses.isEmpty() || statuses.contains(p.status()))
                .filter(p -> needle == null
                        || p.id().toString().toLowerCase(java.util.Locale.ROOT).contains(needle)
                        || (p.providerPaymentId() != null
                        && p.providerPaymentId().toLowerCase(java.util.Locale.ROOT).contains(needle))
                        || (p.providerOrderId() != null
                        && p.providerOrderId().toLowerCase(java.util.Locale.ROOT).contains(needle)))
                .sorted(Comparator.comparing(Payment::createdAt).reversed())
                .toList();
        return pageOf(matches, page);
    }

    @Override
    public long count() {
        return byId.size();
    }

    @Override
    public long countByStatus(PaymentStatus status) {
        return byId.values().stream().filter(p -> p.status() == status).count();
    }

    private static PageResult<Payment> pageOf(List<Payment> all, PageQuery page) {
        int size = page == null ? 20 : page.size();
        int index = page == null ? 0 : page.page();
        int from = Math.min(index * size, all.size());
        int to = Math.min(from + size, all.size());
        return PageResult.of(all.subList(from, to), index, size, all.size());
    }
}
