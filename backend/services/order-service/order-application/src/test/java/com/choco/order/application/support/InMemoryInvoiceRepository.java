package com.choco.order.application.support;

import com.choco.core.page.PageResult;
import com.choco.core.port.PageQuery;
import com.choco.order.domain.invoice.Invoice;
import com.choco.order.domain.port.InvoiceRepository;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** In-memory invoice store with the same scoping semantics the JPA adapter implements. */
public class InMemoryInvoiceRepository implements InvoiceRepository {

    private final Map<UUID, Invoice> byId = new LinkedHashMap<>();

    @Override
    public Invoice save(Invoice invoice) {
        byId.put(invoice.id(), invoice);
        return invoice;
    }

    @Override
    public Optional<Invoice> findById(UUID id) {
        return Optional.ofNullable(byId.get(id));
    }

    @Override
    public Optional<Invoice> findByOrderId(UUID orderId) {
        return byId.values().stream().filter(i -> i.orderId().equals(orderId)).findFirst();
    }

    @Override
    public Optional<Invoice> findByIdAndCustomerId(UUID id, UUID customerId) {
        return byId.values().stream()
                .filter(i -> i.id().equals(id) && i.customerId().equals(customerId))
                .findFirst();
    }

    @Override
    public PageResult<Invoice> findByCustomer(UUID customerId, PageQuery page) {
        List<Invoice> matching = byId.values().stream()
                .filter(i -> i.customerId().equals(customerId))
                .sorted(Comparator.comparing(Invoice::issuedAt).reversed())
                .toList();
        return paginate(matching, page);
    }

    @Override
    public PageResult<Invoice> findAll(PageQuery page) {
        List<Invoice> matching = byId.values().stream()
                .sorted(Comparator.comparing(Invoice::issuedAt).reversed())
                .toList();
        return paginate(matching, page);
    }

    @Override
    public boolean existsByOrderId(UUID orderId) {
        return byId.values().stream().anyMatch(i -> i.orderId().equals(orderId));
    }

    @Override
    public long count() {
        return byId.size();
    }

    private static PageResult<Invoice> paginate(List<Invoice> matching, PageQuery page) {
        int from = Math.min(page.offset(), matching.size());
        int to = Math.min(from + page.size(), matching.size());
        return PageResult.of(matching.subList(from, to), page.page(), page.size(), matching.size());
    }
}
