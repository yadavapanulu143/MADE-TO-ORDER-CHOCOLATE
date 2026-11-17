package com.choco.payment.adapter.in.web;

import com.choco.core.page.PageResult;
import com.choco.core.port.PageQuery;
import com.choco.payment.application.command.RequestRefundCommand;
import com.choco.payment.application.dto.PaymentCountersView;
import com.choco.payment.application.dto.PaymentSummaryView;
import com.choco.payment.application.dto.PaymentView;
import com.choco.payment.application.dto.RefundResultView;
import com.choco.payment.application.service.PaymentQueryService;
import com.choco.payment.application.service.RefundService;
import com.choco.payment.domain.payment.PaymentStatus;
import com.choco.security.principal.AuthenticatedUser;
import com.choco.security.principal.CurrentUserProvider;
import com.choco.security.principal.Roles;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The seller's payment surface.
 *
 * <p>Class-level role check, so a customer token cannot reach any of these paths however the methods
 * are annotated — the same defect the order service's admin controller had, fixed here before it could
 * recur. The per-action refund permission narrows the money-moving call further.
 */
@RestController
@RequestMapping("/api/v1/admin/payments")
@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
@Tag(name = "Payments (admin)", description = "The seller's payment records, refunds and counters")
class AdminPaymentController {

    private final PaymentQueryService paymentQueryService;
    private final RefundService refundService;
    private final CurrentUserProvider currentUserProvider;

    AdminPaymentController(PaymentQueryService paymentQueryService, RefundService refundService,
                           CurrentUserProvider currentUserProvider) {
        this.paymentQueryService = paymentQueryService;
        this.refundService = refundService;
        this.currentUserProvider = currentUserProvider;
    }

    @GetMapping
    @Operation(summary = "A page of payments, optionally narrowed by status and a search term")
    ResponseEntity<PageResult<PaymentSummaryView>> list(
            @RequestParam(required = false) List<PaymentStatus> status,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) Boolean ascending) {
        Collection<PaymentStatus> statuses = status;
        return ResponseEntity.ok(paymentQueryService.searchForAdmin(statuses, search,
                PageQuery.of(page, size, sort, ascending)));
    }

    @GetMapping("/counters")
    @Operation(summary = "The payment counters the seller dashboard shows")
    ResponseEntity<PaymentCountersView> counters() {
        return ResponseEntity.ok(paymentQueryService.counters());
    }

    @GetMapping("/{paymentId}")
    @Operation(summary = "Read one payment, with its attempts, refunds and disputes")
    ResponseEntity<PaymentView> get(@PathVariable UUID paymentId) {
        return ResponseEntity.ok(paymentQueryService.forAdmin(paymentId));
    }

    @PostMapping("/{paymentId}/refunds")
    @PreAuthorize("hasAuthority('" + Roles.PERM_PAYMENT_REFUND + "')")
    @Operation(summary = "Refund part or all of a captured payment")
    ResponseEntity<RefundResultView> refund(@PathVariable UUID paymentId,
                                            @Valid @RequestBody RefundRequest request) {
        AuthenticatedUser caller = currentUserProvider.requireUser();
        return ResponseEntity.ok(refundService.refund(new RequestRefundCommand(paymentId,
                request.amountMinorUnits(), request.reason(), caller.userId().toString())));
    }
}
