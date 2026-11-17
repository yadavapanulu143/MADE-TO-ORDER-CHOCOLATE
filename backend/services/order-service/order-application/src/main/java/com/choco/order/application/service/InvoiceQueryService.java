package com.choco.order.application.service;

import com.choco.core.error.ErrorCode;
import com.choco.core.error.NotFoundException;
import com.choco.core.page.PageResult;
import com.choco.core.port.PageQuery;
import com.choco.order.application.dto.InvoiceView;
import com.choco.order.domain.invoice.Invoice;
import com.choco.order.domain.port.InvoiceRepository;
import java.util.UUID;

/**
 * The read side of invoices.
 *
 * <p>Ownership is part of the query, not a check afterwards. {@link #ownInvoice} looks the invoice up
 * by id <em>and</em> customer id, and a cross-customer id finds nothing rather than being returned and
 * then rejected. The response is a {@code 404}, so it does not confirm that another customer's invoice
 * exists — the same rule the order and address reads follow.
 */
public class InvoiceQueryService {

    private final InvoiceRepository invoiceRepository;

    public InvoiceQueryService(InvoiceRepository invoiceRepository) {
        this.invoiceRepository = invoiceRepository;
    }

    /** One of the caller's own invoices. A cross-customer id is reported as not found. */
    public InvoiceView ownInvoice(UUID customerId, UUID invoiceId) {
        Invoice invoice = invoiceRepository.findByIdAndCustomerId(invoiceId, customerId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.NOT_FOUND, "Invoice not found"));
        return InvoiceViewMapper.toView(invoice);
    }

    /** The caller's own invoice for one of their orders. */
    public InvoiceView ownInvoiceForOrder(UUID customerId, UUID orderId) {
        Invoice invoice = invoiceRepository.findByOrderId(orderId)
                .filter(found -> found.customerId().equals(customerId))
                .orElseThrow(() -> new NotFoundException(ErrorCode.NOT_FOUND, "Invoice not found"));
        return InvoiceViewMapper.toView(invoice);
    }

    /** A page of the caller's own invoices, newest first. */
    public PageResult<InvoiceView> ownInvoices(UUID customerId, PageQuery page) {
        return invoiceRepository.findByCustomer(customerId, page).map(InvoiceViewMapper::toView);
    }

    /** Any invoice, for the seller. */
    public InvoiceView forAdmin(UUID invoiceId) {
        Invoice invoice = invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.NOT_FOUND, "Invoice not found"));
        return InvoiceViewMapper.toView(invoice);
    }

    /** A page of every invoice, for the seller. */
    public PageResult<InvoiceView> allForAdmin(PageQuery page) {
        return invoiceRepository.findAll(page).map(InvoiceViewMapper::toView);
    }

    public long count() {
        return invoiceRepository.count();
    }
}
