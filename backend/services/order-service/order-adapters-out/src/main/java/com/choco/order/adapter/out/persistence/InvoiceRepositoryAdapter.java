package com.choco.order.adapter.out.persistence;

import com.choco.core.page.PageResult;
import com.choco.core.port.PageQuery;
import com.choco.order.domain.invoice.Invoice;
import com.choco.order.domain.port.InvoiceRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * JPA implementation of {@link InvoiceRepository}.
 *
 * <p>Reads are {@code @Transactional(readOnly = true)}: the line collection is lazy and the mapper
 * touches it, so without a transaction the session would already be closed when the mapping runs.
 * Opening the transaction here, where the mapping happens, keeps the session alive for exactly as long
 * as it is needed.
 */
@Repository
class InvoiceRepositoryAdapter implements InvoiceRepository {

    private final SpringDataInvoiceRepository delegate;

    InvoiceRepositoryAdapter(SpringDataInvoiceRepository delegate) {
        this.delegate = delegate;
    }

    @Override
    public Invoice save(Invoice invoice) {
        delegate.save(InvoicePersistenceMapper.toEntity(invoice));
        return invoice;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Invoice> findById(UUID id) {
        return delegate.findById(id).map(InvoicePersistenceMapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Invoice> findByOrderId(UUID orderId) {
        return delegate.findByOrderId(orderId).map(InvoicePersistenceMapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Invoice> findByIdAndCustomerId(UUID id, UUID customerId) {
        return delegate.findByIdAndCustomerId(id, customerId).map(InvoicePersistenceMapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<Invoice> findByCustomer(UUID customerId, PageQuery page) {
        Page<InvoiceJpaEntity> result = delegate.findByCustomerId(customerId, toPageable(page));
        return toPageResult(result);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<Invoice> findAll(PageQuery page) {
        Page<InvoiceJpaEntity> result = delegate.findAll(toPageable(page));
        return toPageResult(result);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsByOrderId(UUID orderId) {
        return delegate.existsByOrderId(orderId);
    }

    @Override
    @Transactional(readOnly = true)
    public long count() {
        return delegate.count();
    }

    private static PageResult<Invoice> toPageResult(Page<InvoiceJpaEntity> result) {
        List<Invoice> invoices = result.getContent().stream()
                .map(InvoicePersistenceMapper::toDomain)
                .toList();
        return PageResult.of(invoices, result.getNumber(), result.getSize(), result.getTotalElements());
    }

    private static PageRequest toPageable(PageQuery query) {
        Sort.Direction direction = query.ascending() ? Sort.Direction.ASC : Sort.Direction.DESC;
        return PageRequest.of(query.page(), query.size(), Sort.by(direction, "issuedAt"));
    }
}
