package com.choco.order.adapter.in.web;

import com.choco.order.application.command.CheckoutCommand;
import com.choco.order.application.dto.CheckoutQuoteView;
import com.choco.order.application.dto.OrderView;
import com.choco.order.application.service.CheckoutService;
import com.choco.security.principal.AuthenticatedUser;
import com.choco.security.principal.CurrentUserProvider;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Checkout: price the cart, then place the order.
 *
 * <p>The quote and the placement run the same checks, so a cart that quotes as serviceable will not
 * fail placement for a reason the customer could have been told before paying. Both take the customer
 * id from the token and the address id from the request, and the use case re-checks that the address
 * belongs to that customer, so an address id cannot be borrowed from another account.
 *
 * <p>Placement accepts an {@code Idempotency-Key} header. A retried request — a flaky network, a
 * double-tap — with the same key returns the first order rather than creating a second, which is the
 * difference between one chocolate order and two on the customer's statement.
 */
@RestController
@RequestMapping("/api/v1/checkout")
@Tag(name = "Checkout", description = "Price the cart and place a made-to-order purchase")
class CheckoutController {

    private final CheckoutService checkoutService;
    private final CurrentUserProvider currentUserProvider;

    CheckoutController(CheckoutService checkoutService, CurrentUserProvider currentUserProvider) {
        this.checkoutService = checkoutService;
        this.currentUserProvider = currentUserProvider;
    }

    @GetMapping("/quote")
    @Operation(summary = "Price the current cart against a delivery address without placing anything")
    ResponseEntity<CheckoutQuoteView> quote(@RequestParam UUID addressId) {
        AuthenticatedUser caller = currentUserProvider.requireUser();
        return ResponseEntity.ok(checkoutService.quote(caller.userId(), addressId));
    }

    @PostMapping
    @Operation(summary = "Place the order, preserving the price confirmed at this moment")
    ResponseEntity<OrderView> place(@Valid @RequestBody CheckoutRequest request,
                                    @RequestHeader(value = "Idempotency-Key", required = false)
                                    String idempotencyKey) {
        AuthenticatedUser caller = currentUserProvider.requireFullyVerifiedCustomer();
        OrderView placed = checkoutService.placeOrder(caller.userId(),
                new CheckoutCommand(request.addressId(), idempotencyKey));
        return ResponseEntity.created(URI.create("/api/v1/orders/" + placed.id())).body(placed);
    }
}
