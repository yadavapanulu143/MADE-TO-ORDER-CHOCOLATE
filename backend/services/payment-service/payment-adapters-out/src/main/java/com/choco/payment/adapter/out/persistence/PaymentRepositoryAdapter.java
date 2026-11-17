package com.choco.payment.adapter.out.persistence;

import com.choco.core.page.PageResult;
import com.choco.core.port.PageQuery;
import com.choco.payment.domain.payment.Payment;
import com.choco.payment.domain.payment.PaymentStatus;
import com.choco.payment.domain.port.PaymentRepository;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * JPA implementation of {@link PaymentRepository}.
 *
 * <p>Every read is {@code @Transactional(readOnly = true)}: the child collections are lazy and the
 * mapper touches them to build the aggregate, so without a transaction the session is closed before
 * the mapping runs and the read fails with a lazy-initialisation error.
 *
 * <p>{@link #save} carries the current row version onto the mapped entity so optimistic locking works.
 * The aggregate holds no version of its own, so without this a second write would send version zero
 * against a row already at one and every subsequent save would fail as a stale object.
 */
@Repository
class PaymentRepositoryAdapter implements PaymentRepository {

    private static final List<String> SORTABLE = List.of("createdAt", "updatedAt", "status");

    private final SpringDataPaymentRepository delegate;

    PaymentRepositoryAdapter(SpringDataPaymentRepository delegate) {
        this.delegate = delegate;
    }

    @Override
    public Payment save(Payment payment) {
        PaymentJpaEntity entity = PaymentPersistenceMapper.toEntity(payment);
        delegate.findVersionById(payment.id()).ifPresent(entity::setVersion);
        delegate.save(entity);
        return payment;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Payment> findById(UUID id) {
        return delegate.findById(id).map(PaymentPersistenceMapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Payment> findByIdAndCustomerId(UUID id, UUID customerId) {
        return delegate.findByIdAndCustomerId(id, customerId).map(PaymentPersistenceMapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Payment> findByOrderId(UUID orderId) {
        return delegate.findByOrderId(orderId).map(PaymentPersistenceMapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Payment> findByProviderOrderId(String providerOrderId) {
        return delegate.findByProviderOrderId(providerOrderId).map(PaymentPersistenceMapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Payment> findByProviderPaymentId(String providerPaymentId) {
        return delegate.findByProviderPaymentId(providerPaymentId).map(PaymentPersistenceMapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<Payment> findByCustomerId(UUID customerId, PageQuery page) {
        Page<PaymentJpaEntity> result = delegate.findByCustomerId(customerId, toPageable(page));
        return toPageResult(result, page);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<Payment> search(Collection<PaymentStatus> statuses, String search, PageQuery page) {
        List<String> names = statuses.stream().map(Enum::name).toList();
        Page<PaymentJpaEntity> result = delegate.search(names, likePattern(search), toPageable(page));
        return toPageResult(result, page);
    }

    @Override
    @Transactional(readOnly = true)
    public long count() {
        return delegate.count();
    }

    @Override
    @Transactional(readOnly = true)
    public long countByStatus(PaymentStatus status) {
        return delegate.countByStatus(status.name());
    }

    private static String likePattern(String search) {
        if (search == null || search.isBlank()) {
            return null;
        }
        String escaped = search.trim().toLowerCase(java.util.Locale.ROOT)
                .replace("!", "!!")
                .replace("%", "!%")
                .replace("_", "!_");
        return "%" + escaped + "%";
    }

    private static PageResult<Payment> toPageResult(Page<PaymentJpaEntity> result, PageQuery query) {
        List<Payment> payments = result.getContent().stream().map(PaymentPersistenceMapper::toDomain).toList();
        return PageResult.of(payments, result.getNumber(), result.getSize(), result.getTotalElements());
    }

    private static PageRequest toPageable(PageQuery query) {
        String sortField = query.sort() == null || !SORTABLE.contains(query.sort()) ? "createdAt" : query.sort();
        Sort.Direction direction = query.ascending() ? Sort.Direction.ASC : Sort.Direction.DESC;
        return PageRequest.of(query.page(), query.size(), Sort.by(direction, sortField));
    }
}
