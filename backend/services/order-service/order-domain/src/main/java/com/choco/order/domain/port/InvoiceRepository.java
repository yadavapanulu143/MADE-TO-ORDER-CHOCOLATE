package com.choco.order.domain.port;

import com.choco.core.page.PageResult;
import com.choco.core.port.PageQuery;
import com.choco.order.domain.invoice.Invoice;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence port for invoices.
 *
 * <p>Declared in the domain, implemented by a JPA adapter. Ownership is part of the query, exactly as
 * for orders: {@link #findByIdAndCustomerId} is the only single-invoice read for a customer, so a
 * guessed invoice id belonging to another customer finds nothing rather than being returned and then
 * checked.
 */
public interface InvoiceRepository {

    Invoice save(Invoice invoice);

    Optional<Invoice> findById(UUID id);

    Optional<Invoice> findByOrderId(UUID orderId);

    /** One invoice, scoped to its owner. A cross-customer id finds nothing. */
    Optional<Invoice> findByIdAndCustomerId(UUID id, UUID customerId);

    /** A page of one customer's invoices, newest first. The ownership filter is part of the query. */
    PageResult<Invoice> findByCustomer(UUID customerId, PageQuery page);

    /** A page of every invoice, for the seller. */
    PageResult<Invoice> findAll(PageQuery page);

    boolean existsByOrderId(UUID orderId);

    long count();
}
