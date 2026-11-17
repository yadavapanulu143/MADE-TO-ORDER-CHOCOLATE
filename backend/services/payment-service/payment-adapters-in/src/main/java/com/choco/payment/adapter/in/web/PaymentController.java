package com.choco.payment.adapter.in.web;

import com.choco.core.page.PageResult;
import com.choco.core.port.PageQuery;
import com.choco.payment.application.command.VerifyPaymentCommand;
import com.choco.payment.application.dto.PaymentInitiationView;
import com.choco.payment.application.dto.PaymentSummaryView;
import com.choco.payment.application.dto.PaymentView;
import com.choco.payment.application.service.PaymentQueryService;
import com.choco.payment.application.service.PaymentService;
import com.choco.security.principal.AuthenticatedUser;
import com.choco.security.principal.CurrentUserProvider;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * A customer's own payments.
 *
 * <p>Everything is nested under {@code /me} and the customer id always comes from the token, so there
 * is no id in the URL to tamper with. A payment lookup is additionally scoped to the caller's own
 * customer id in the query, so a guessed payment id belonging to someone else is simply not found.
 *
 * <p>Verification is the callback the storefront makes after the provider's checkout. It reports the
 * provider's parameters, and the service decides — from the signature and from the provider's own
 * answer — whether the payment is real. The customer's word is never taken as proof.
 */
@RestController
@RequestMapping("/api/v1/payments/me")
@Tag(name = "Payments", description = "A customer's own payments")
class PaymentController {

    private final PaymentService paymentService;
    private final PaymentQueryService paymentQueryService;
    private final CurrentUserProvider currentUserProvider;

    PaymentController(PaymentService paymentService, PaymentQueryService paymentQueryService,
                      CurrentUserProvider currentUserProvider) {
        this.paymentService = paymentService;
        this.paymentQueryService = paymentQueryService;
        this.currentUserProvider = currentUserProvider;
    }

    @GetMapping
    @Operation(summary = "A page of the signed-in customer's payments, newest first")
    ResponseEntity<PageResult<PaymentSummaryView>> history(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) Boolean ascending) {
        AuthenticatedUser caller = currentUserProvider.requireUser();
        return ResponseEntity.ok(paymentQueryService.ownHistory(caller.userId(),
                PageQuery.of(page, size, sort, ascending)));
    }

    @GetMapping("/{paymentId}")
    @Operation(summary = "Read one of the signed-in customer's payments, with its attempts and refunds")
    ResponseEntity<PaymentView> get(@PathVariable UUID paymentId) {
        AuthenticatedUser caller = currentUserProvider.requireUser();
        return ResponseEntity.ok(paymentQueryService.ownPayment(caller.userId(), paymentId));
    }

    @GetMapping("/orders/{orderId}")
    @Operation(summary = "Read the signed-in customer's payment for one of their orders")
    ResponseEntity<PaymentView> forOrder(@PathVariable UUID orderId) {
        AuthenticatedUser caller = currentUserProvider.requireUser();
        return ResponseEntity.ok(paymentQueryService.ownPaymentForOrder(caller.userId(), orderId));
    }

    @PostMapping("/orders/{orderId}/verify")
    @Operation(summary = "Verify the provider's checkout response and record the true payment outcome")
    ResponseEntity<PaymentView> verify(@PathVariable UUID orderId,
                                       @Valid @RequestBody VerifyPaymentRequest request) {
        AuthenticatedUser caller = currentUserProvider.requireFullyVerifiedCustomer();
        return ResponseEntity.ok(paymentService.verify(caller.userId(), new VerifyPaymentCommand(
                orderId, request.providerOrderId(), request.providerPaymentId(), request.signature())));
    }
}
