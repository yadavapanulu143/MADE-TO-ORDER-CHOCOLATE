package com.choco.order.adapter.in.web;

import com.choco.order.application.dto.PaymentInitiationView;
import com.choco.order.application.service.PaymentInitiationService;
import com.choco.security.principal.AuthenticatedUser;
import com.choco.security.principal.CurrentUserProvider;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Starts the payment for one of the customer's own orders.
 *
 * <p>This is the step the storefront calls after placement and before it opens the provider's checkout.
 * The customer id always comes from the token and the order id from the path, and the use case asserts
 * the order belongs to the caller, so a guessed order id cannot be paid for by someone else.
 *
 * <p>The order's own total is what is collected — never a number from this request, which carries no
 * body at all. A caller cannot ask to be charged less than the order costs.
 *
 * <p>Accepts an {@code Idempotency-Key} and forwards it, so a retried call returns the first provider
 * order rather than creating a second charge.
 */
@RestController
@RequestMapping("/api/v1/orders/me")
@Tag(name = "Orders", description = "A customer's own orders, payment, cancellation and refunds")
class OrderPaymentController {

    private final PaymentInitiationService paymentInitiationService;
    private final CurrentUserProvider currentUserProvider;

    OrderPaymentController(PaymentInitiationService paymentInitiationService,
                           CurrentUserProvider currentUserProvider) {
        this.paymentInitiationService = paymentInitiationService;
        this.currentUserProvider = currentUserProvider;
    }

    @PostMapping("/{orderId}/payment")
    @Operation(summary = "Start a payment for this order and return the provider checkout parameters")
    ResponseEntity<PaymentInitiationView> initiatePayment(
            @PathVariable UUID orderId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        AuthenticatedUser caller = currentUserProvider.requireFullyVerifiedCustomer();
        return ResponseEntity.ok(paymentInitiationService.initiate(caller.userId(), orderId,
                idempotencyKey));
    }
}
