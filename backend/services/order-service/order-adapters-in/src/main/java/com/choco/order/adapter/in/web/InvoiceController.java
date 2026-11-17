package com.choco.order.adapter.in.web;

import com.choco.core.page.PageResult;
import com.choco.core.port.PageQuery;
import com.choco.order.application.dto.InvoiceView;
import com.choco.order.application.service.InvoiceQueryService;
import com.choco.security.principal.AuthenticatedUser;
import com.choco.security.principal.CurrentUserProvider;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * A customer's own invoices.
 *
 * <p>Everything is nested under {@code /me} and the customer id always comes from the token, so there
 * is no id in the URL to tamper with. The query service additionally scopes a single-invoice lookup by
 * customer id, so a guessed invoice id belonging to someone else is reported as not found rather than
 * returned.
 *
 * <p>There is deliberately no customer endpoint that issues an invoice: an invoice is issued when the
 * seller's verified payment confirms the order, not on request. A customer can only read their own.
 */
@RestController
@RequestMapping("/api/v1/invoices/me")
@Tag(name = "Invoices", description = "A customer's own invoices")
class InvoiceController {

    private final InvoiceQueryService invoiceQueryService;
    private final CurrentUserProvider currentUserProvider;

    InvoiceController(InvoiceQueryService invoiceQueryService, CurrentUserProvider currentUserProvider) {
        this.invoiceQueryService = invoiceQueryService;
        this.currentUserProvider = currentUserProvider;
    }

    @GetMapping
    @Operation(summary = "A page of the signed-in customer's invoices, newest first")
    ResponseEntity<PageResult<InvoiceView>> list(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) Boolean ascending) {
        AuthenticatedUser caller = currentUserProvider.requireUser();
        return ResponseEntity.ok(invoiceQueryService.ownInvoices(caller.userId(),
                PageQuery.of(page, size, sort, ascending)));
    }

    @GetMapping("/{invoiceId}")
    @Operation(summary = "Read one of the signed-in customer's invoices")
    ResponseEntity<InvoiceView> get(@PathVariable UUID invoiceId) {
        AuthenticatedUser caller = currentUserProvider.requireUser();
        return ResponseEntity.ok(invoiceQueryService.ownInvoice(caller.userId(), invoiceId));
    }

    @GetMapping("/by-order/{orderId}")
    @Operation(summary = "Read the signed-in customer's invoice for one of their orders")
    ResponseEntity<InvoiceView> byOrder(@PathVariable UUID orderId) {
        AuthenticatedUser caller = currentUserProvider.requireUser();
        return ResponseEntity.ok(invoiceQueryService.ownInvoiceForOrder(caller.userId(), orderId));
    }
}
