package com.choco.order.application.service;

import com.choco.core.error.BusinessRuleException;
import com.choco.core.error.ErrorCode;
import com.choco.core.error.NotFoundException;
import com.choco.core.port.Clock;
import com.choco.core.port.EventPublisher;
import com.choco.core.port.IdGenerator;
import com.choco.core.port.TransactionRunner;
import com.choco.order.application.dto.InvoiceView;
import com.choco.order.application.port.AuditLogPort;
import com.choco.order.application.port.InvoiceNumberGenerator;
import com.choco.order.domain.invoice.Invoice;
import com.choco.order.domain.invoice.InvoiceLine;
import com.choco.order.domain.order.DeliveryAddress;
import com.choco.order.domain.order.Order;
import com.choco.order.domain.order.OrderLine;
import com.choco.order.domain.port.InvoiceRepository;
import com.choco.order.domain.port.OrderRepository;
import java.util.List;
import java.util.UUID;

/**
 * Issues invoices.
 *
 * <p>An invoice is issued from a confirmed order, once, and is never edited afterwards. Issuance is
 * idempotent by order id: a redelivered {@code payment.captured} — a broker retry, or a consumer
 * restart — finds the invoice already there and issues nothing new, so a customer cannot end up with
 * two documents for one order and two {@code order.invoice-issued} events.
 *
 * <p>Only a paid order is invoiced. An unpaid order has nothing to bill, so asking for its invoice is a
 * refusal, not an empty document.
 *
 * <p>The document is a snapshot: the customer's details, the delivery address and every priced line are
 * copied out of the order (which is itself a snapshot of the catalogue) at the moment of issuance, so
 * re-rendering it later shows exactly what was billed whatever the catalogue or the customer's address
 * book says by then.
 */
public class InvoiceService {

    private static final String ACTOR_SYSTEM = "SYSTEM";
    private static final String ACTOR_ADMIN = "ADMIN";
    private static final String TARGET = "INVOICE";

    private final OrderRepository orderRepository;
    private final InvoiceRepository invoiceRepository;
    private final InvoiceNumberGenerator invoiceNumberGenerator;
    private final EventPublisher eventPublisher;
    private final AuditLogPort auditLog;
    private final Clock clock;
    private final IdGenerator idGenerator;
    private final TransactionRunner transactions;

    public InvoiceService(OrderRepository orderRepository, InvoiceRepository invoiceRepository,
                          InvoiceNumberGenerator invoiceNumberGenerator, EventPublisher eventPublisher,
                          AuditLogPort auditLog, Clock clock, IdGenerator idGenerator,
                          TransactionRunner transactions) {
        this.orderRepository = orderRepository;
        this.invoiceRepository = invoiceRepository;
        this.invoiceNumberGenerator = invoiceNumberGenerator;
        this.eventPublisher = eventPublisher;
        this.auditLog = auditLog;
        this.clock = clock;
        this.idGenerator = idGenerator;
        this.transactions = transactions;
    }

    /**
     * Issues the invoice for an order, or returns the one that already exists.
     *
     * <p>Called after a verified payment confirms an order. Idempotent, so it is safe to call from a
     * consumer that may see the same event twice.
     */
    public InvoiceView issueForOrder(UUID orderId) {
        return transactions.execute(() -> {
            Invoice existing = invoiceRepository.findByOrderId(orderId).orElse(null);
            if (existing != null) {
                return InvoiceViewMapper.toView(existing);
            }
            Order order = requireOrder(orderId);
            if (!order.isPaid()) {
                throw new BusinessRuleException(ErrorCode.CONFLICT,
                        "An invoice can only be issued for a paid order");
            }
            Invoice invoice = buildInvoice(order);
            Invoice saved = invoiceRepository.save(invoice);
            eventPublisher.publishAll(invoice.pullDomainEvents());
            audit(ACTOR_SYSTEM, "payment-service", "invoice.issue", saved, "SUCCESS",
                    saved.invoiceNumber());
            return InvoiceViewMapper.toView(saved);
        });
    }

    /** The seller issues an invoice for an already-confirmed order, e.g. one that predates issuance. */
    public InvoiceView issueAsAdmin(String adminId, UUID orderId) {
        return transactions.execute(() -> {
            Invoice existing = invoiceRepository.findByOrderId(orderId).orElse(null);
            if (existing != null) {
                return InvoiceViewMapper.toView(existing);
            }
            Order order = requireOrder(orderId);
            if (!order.isPaid()) {
                throw new BusinessRuleException(ErrorCode.CONFLICT,
                        "An invoice can only be issued for a paid order");
            }
            Invoice invoice = buildInvoice(order);
            Invoice saved = invoiceRepository.save(invoice);
            eventPublisher.publishAll(invoice.pullDomainEvents());
            audit(ACTOR_ADMIN, adminId, "invoice.issue", saved, "SUCCESS", saved.invoiceNumber());
            return InvoiceViewMapper.toView(saved);
        });
    }

    private Invoice buildInvoice(Order order) {
        DeliveryAddress address = order.deliveryAddress();
        Invoice.CustomerDetails customer = new Invoice.CustomerDetails(
                order.customer().fullName(), order.customer().email(), order.customer().mobile());
        Invoice.InvoiceAddress billing = new Invoice.InvoiceAddress(
                address.recipientName(), address.mobile(), address.line1(), address.line2(),
                address.city(), address.state(), address.pincode());
        List<InvoiceLine> lines = order.lines().stream().map(InvoiceService::toInvoiceLine).toList();
        return Invoice.issue(
                idGenerator.nextId(),
                invoiceNumberGenerator.next(clock.now()),
                order.id(),
                order.orderNumber(),
                order.customerId(),
                customer,
                billing,
                lines,
                order.deliveryCharge(),
                clock.now());
    }

    private static InvoiceLine toInvoiceLine(OrderLine line) {
        return new InvoiceLine(line.productId(), line.variantId(), line.productName(),
                line.variantLabel(), line.sku(), line.unitPrice(), line.quantity());
    }

    private Order requireOrder(UUID orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.NOT_FOUND, "Order not found"));
    }

    private void audit(String actorType, String actorId, String action, Invoice invoice, String outcome,
                       String detail) {
        auditLog.record(actorType, actorId, action, TARGET, invoice.id().toString(), outcome, detail, null);
    }
}
