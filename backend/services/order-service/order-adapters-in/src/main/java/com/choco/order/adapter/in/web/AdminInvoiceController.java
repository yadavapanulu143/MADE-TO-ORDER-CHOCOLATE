package com.choco.order.adapter.in.web;

import com.choco.core.page.PageResult;
import com.choco.core.port.PageQuery;
import com.choco.order.application.dto.InvoiceView;
import com.choco.order.application.service.InvoiceQueryService;
import com.choco.order.application.service.InvoiceService;
import com.choco.security.principal.AuthenticatedUser;
import com.choco.security.principal.CurrentUserProvider;
import com.choco.security.principal.Roles;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Invoice administration: the seller's view of every invoice.
 *
 * <p>Reading needs only an authenticated administrator. Issuing an invoice by hand needs
 * {@code order:write}, the same authority that lets an administrator act on an order, because issuing
 * a document for an order is an order-side action. The acting administrator's id is read from the
 * token for the audit trail; it is never taken from the request.
 *
 * <p>Issuance is idempotent by order id, so calling issue for an order that already has an invoice
 * returns the existing document rather than creating a second one.
 */
@RestController
@RequestMapping("/api/v1/admin/invoices")
@PreAuthorize("hasAnyRole('" + Roles.ADMIN + "', '" + Roles.SUPER_ADMIN + "')")
@Tag(name = "Invoice administration", description = "The seller's invoice view")
class AdminInvoiceController {

    private final InvoiceQueryService invoiceQueryService;
    private final InvoiceService invoiceService;
    private final CurrentUserProvider currentUserProvider;

    AdminInvoiceController(InvoiceQueryService invoiceQueryService, InvoiceService invoiceService,
                           CurrentUserProvider currentUserProvider) {
        this.invoiceQueryService = invoiceQueryService;
        this.invoiceService = invoiceService;
        this.currentUserProvider = currentUserProvider;
    }

    @GetMapping
    @Operation(summary = "A page of every invoice, newest first")
    ResponseEntity<PageResult<InvoiceView>> list(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) Boolean ascending) {
        return ResponseEntity.ok(invoiceQueryService.allForAdmin(
                PageQuery.of(page, size, sort, ascending)));
    }

    @GetMapping("/{invoiceId}")
    @Operation(summary = "Read a complete invoice")
    ResponseEntity<InvoiceView> get(@PathVariable UUID invoiceId) {
        return ResponseEntity.ok(invoiceQueryService.forAdmin(invoiceId));
    }

    @PostMapping("/order/{orderId}")
    @PreAuthorize("hasAuthority('" + Roles.PERM_ORDER_WRITE + "')")
    @Operation(summary = "Issue (or return the existing) invoice for a confirmed order")
    ResponseEntity<InvoiceView> issue(@PathVariable UUID orderId) {
        AuthenticatedUser caller = currentUserProvider.requireUser();
        return ResponseEntity.ok(invoiceService.issueAsAdmin(caller.userId().toString(), orderId));
    }
}
