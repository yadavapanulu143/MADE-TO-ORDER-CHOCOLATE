package com.choco.reporting.application.service;

import com.choco.core.error.ErrorCode;
import com.choco.core.error.ForbiddenException;
import com.choco.core.port.Clock;
import com.choco.reporting.application.port.AuditLogPort;
import com.choco.reporting.application.port.ExportWriter;
import com.choco.reporting.domain.export.ExportColumn;
import com.choco.reporting.domain.export.ExportDataset;
import com.choco.reporting.domain.export.ExportFormat;
import com.choco.reporting.domain.export.ExportType;
import com.choco.reporting.domain.model.CustomerProjection;
import com.choco.reporting.domain.model.InvoiceProjection;
import com.choco.reporting.domain.model.OrderProjection;
import com.choco.reporting.domain.model.PaymentProjection;
import com.choco.reporting.domain.port.ReportingReadModel;
import com.choco.reporting.domain.report.ReportLimits;
import com.choco.reporting.domain.report.ReportPeriod;
import com.choco.security.principal.Roles;
import java.util.ArrayList;
import java.util.List;

/**
 * Builds and authorises customer and operational exports.
 *
 * <p>Two controls sit on this path, and both are load-bearing.
 *
 * <p>First, <b>authorisation</b>. A customer export names people, so it needs the {@code customer:export}
 * permission, which the seller grants deliberately. The operational exports (orders, payments,
 * invoices) are lower-risk but still gated behind report access. The permission is checked here, in the
 * use case, not only on the controller, so a second caller cannot reach the data by a different door.
 *
 * <p>Second, <b>minimisation</b>. Each export declares its columns, and the customer export masks the
 * email and mobile. The dataset is built from the declared columns only, so a query that happened to
 * return more cannot leak more. The {@link ExportColumn} guard refuses any column that names an
 * authentication artefact, and the audit row records who pulled the data.
 */
public class ExportService {

    private static final String ACTOR_ADMIN = "ADMIN";
    private static final String TARGET = "EXPORT";

    private final ReportingReadModel readModel;
    private final ExportWriter exportWriter;
    private final AuditLogPort auditLog;
    private final Clock clock;

    public ExportService(ReportingReadModel readModel, ExportWriter exportWriter, AuditLogPort auditLog,
                         Clock clock) {
        this.readModel = readModel;
        this.exportWriter = exportWriter;
        this.auditLog = auditLog;
        this.clock = clock;
    }

    /**
     * Produces the export document.
     *
     * @param permissions the caller's granted permissions, checked here as well as at the edge
     * @param actorId     the administrator the audit row attributes the export to
     */
    public ExportDocument export(ExportType type, ExportFormat format, ReportPeriod period,
                                 java.util.Set<String> permissions, boolean superAdmin, String actorId) {
        requirePermission(type, permissions, superAdmin);
        ExportDataset dataset = buildDataset(type, period);
        byte[] bytes = exportWriter.write(dataset, format);
        String filename = type.name().toLowerCase(java.util.Locale.ROOT) + "-"
                + period.from().toString().substring(0, 10) + "." + format.extension();
        auditLog.record(ACTOR_ADMIN, actorId, "export." + type.name().toLowerCase(java.util.Locale.ROOT),
                TARGET, type.name(), "SUCCESS",
                dataset.rowCount() + " rows as " + format.name(), null);
        return new ExportDocument(filename, format.contentType(), bytes, dataset.rowCount());
    }

    private void requirePermission(ExportType type, java.util.Set<String> permissions, boolean superAdmin) {
        if (superAdmin) {
            return;
        }
        // The customer export is the sensitive one; everything else needs report access.
        String required = type == ExportType.CUSTOMERS ? Roles.PERM_CUSTOMER_EXPORT : Roles.PERM_REPORT_READ;
        if (permissions == null || !permissions.contains(required)) {
            throw new ForbiddenException("Missing required permission: " + required);
        }
    }

    private ExportDataset buildDataset(ExportType type, ReportPeriod period) {
        return switch (type) {
            case CUSTOMERS -> customerDataset(period);
            case ORDERS -> orderDataset(period);
            case PAYMENTS -> paymentDataset(period);
            case INVOICES -> invoiceDataset(period);
        };
    }

    private ExportDataset customerDataset(ReportPeriod period) {
        List<ExportColumn> columns = List.of(
                new ExportColumn("customerId", "Customer id"),
                new ExportColumn("fullName", "Name"),
                new ExportColumn("email", "Email (masked)"),
                new ExportColumn("mobile", "Mobile (masked)"),
                new ExportColumn("emailVerified", "Email verified"),
                new ExportColumn("mobileVerified", "Mobile verified"),
                new ExportColumn("registeredAt", "Registered at"));
        List<List<Object>> rows = new ArrayList<>();
        List<CustomerProjection> customers =
                readModel.findCustomersRegisteredBetween(period.from(), period.to(), ReportLimits.MAX_REPORT_ROWS);
        ReportLimits.requireWithinLimit(customers.size());
        for (CustomerProjection customer : customers) {
            rows.add(List.of(
                    customer.customerId(),
                    customer.fullName() == null ? "" : customer.fullName(),
                    Masking.email(customer.email()),
                    Masking.mobile(customer.mobile()),
                    customer.emailVerified(),
                    customer.mobileVerified(),
                    customer.registeredAt() == null ? "" : customer.registeredAt().toString()));
        }
        return new ExportDataset("customers", columns, rows);
    }

    private ExportDataset orderDataset(ReportPeriod period) {
        List<ExportColumn> columns = List.of(
                new ExportColumn("orderNumber", "Order number"),
                new ExportColumn("status", "Status"),
                new ExportColumn("totalMinorUnits", "Total (paise)"),
                new ExportColumn("currency", "Currency"),
                new ExportColumn("placedAt", "Placed at"));
        List<List<Object>> rows = new ArrayList<>();
        List<OrderProjection> orders = readModel.findOrdersBetween(period.from(), period.to(), ReportLimits.MAX_REPORT_ROWS);
        ReportLimits.requireWithinLimit(orders.size());
        for (OrderProjection order : orders) {
            rows.add(List.of(order.orderNumber(), order.status(), order.totalMinorUnits(),
                    order.currency(), order.placedAt() == null ? "" : order.placedAt().toString()));
        }
        return new ExportDataset("orders", columns, rows);
    }

    private ExportDataset paymentDataset(ReportPeriod period) {
        List<ExportColumn> columns = List.of(
                new ExportColumn("orderId", "Order"),
                new ExportColumn("status", "Status"),
                new ExportColumn("amountMinorUnits", "Amount (paise)"),
                new ExportColumn("currency", "Currency"),
                new ExportColumn("method", "Method"),
                new ExportColumn("occurredAt", "Occurred at"));
        List<List<Object>> rows = new ArrayList<>();
        List<PaymentProjection> payments =
                readModel.findPaymentsBetween(period.from(), period.to(), ReportLimits.MAX_REPORT_ROWS);
        ReportLimits.requireWithinLimit(payments.size());
        for (PaymentProjection payment : payments) {
            rows.add(List.of(payment.orderId(), payment.status(), payment.amountMinorUnits(),
                    payment.currency(), payment.method() == null ? "" : payment.method(),
                    payment.occurredAt() == null ? "" : payment.occurredAt().toString()));
        }
        return new ExportDataset("payments", columns, rows);
    }

    private ExportDataset invoiceDataset(ReportPeriod period) {
        List<ExportColumn> columns = List.of(
                new ExportColumn("invoiceNumber", "Invoice number"),
                new ExportColumn("orderNumber", "Order number"),
                new ExportColumn("totalMinorUnits", "Total (paise)"),
                new ExportColumn("currency", "Currency"),
                new ExportColumn("issuedAt", "Issued at"));
        List<List<Object>> rows = new ArrayList<>();
        List<InvoiceProjection> invoices =
                readModel.findInvoicesBetween(period.from(), period.to(), ReportLimits.MAX_REPORT_ROWS);
        ReportLimits.requireWithinLimit(invoices.size());
        for (InvoiceProjection invoice : invoices) {
            rows.add(List.of(invoice.invoiceNumber(), invoice.orderNumber(), invoice.totalMinorUnits(),
                    invoice.currency(), invoice.issuedAt() == null ? "" : invoice.issuedAt().toString()));
        }
        return new ExportDataset("invoices", columns, rows);
    }

    /** A finished document: its bytes, its content type, a suggested filename and its row count. */
    public record ExportDocument(String filename, String contentType, byte[] bytes, int rowCount) {
    }
}
