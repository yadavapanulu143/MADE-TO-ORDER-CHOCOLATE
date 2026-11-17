package com.choco.payment.adapter.in.web;

import com.choco.payment.application.command.InitiatePaymentCommand;
import com.choco.payment.application.dto.PaymentInitiationView;
import com.choco.payment.application.service.PaymentService;
import com.choco.security.principal.AuthenticatedUser;
import com.choco.security.principal.CurrentUserProvider;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Starts a payment for an order.
 *
 * <p>Called by the order service on the customer's behalf, forwarding the customer's own token rather
 * than holding a privileged credential. The customer id is therefore taken from the token, never from
 * the body, so one customer cannot start a payment against another's order — the order service checks
 * ownership too, but this endpoint does not depend on that check having been made.
 *
 * <p>The amount comes from the order service, which is the authority on what an order costs: the
 * figure the customer agreed to pay at checkout is the figure the provider is asked to collect.
 *
 * <p>Accepts an {@code Idempotency-Key}: the order service retries this call after a timeout, and a
 * second provider order for the same customer order would be a real double-charge.
 */
@RestController
@RequestMapping("/api/v1/payments/orders")
@Tag(name = "Payments (internal)", description = "Start a payment for an order")
class InternalPaymentController {

    private final PaymentService paymentService;
    private final CurrentUserProvider currentUserProvider;

    InternalPaymentController(PaymentService paymentService, CurrentUserProvider currentUserProvider) {
        this.paymentService = paymentService;
        this.currentUserProvider = currentUserProvider;
    }

    @PostMapping("/{orderId}/initiate")
    @Operation(summary = "Create a provider order and a local payment for an order")
    ResponseEntity<PaymentInitiationView> initiate(
            @PathVariable UUID orderId,
            @Valid @RequestBody InitiatePaymentRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        AuthenticatedUser caller = currentUserProvider.requireFullyVerifiedCustomer();
        PaymentInitiationView view = paymentService.initiate(new InitiatePaymentCommand(
                orderId, caller.userId(), request.amountMinorUnits(), request.currency(),
                request.receipt(), idempotencyKey));
        return ResponseEntity.ok(view);
    }
}
