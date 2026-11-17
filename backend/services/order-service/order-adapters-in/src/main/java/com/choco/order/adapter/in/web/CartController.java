package com.choco.order.adapter.in.web;

import com.choco.order.application.command.AddToCartCommand;
import com.choco.order.application.command.UpdateCartLineCommand;
import com.choco.order.application.dto.CartView;
import com.choco.order.application.service.CartService;
import com.choco.security.principal.AuthenticatedUser;
import com.choco.security.principal.CurrentUserProvider;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The signed-in customer's cart.
 *
 * <p>There is no cart id in any path. The cart is looked up by the customer id taken from the token,
 * so a customer has no id to swap for another's and the ownership check is the lookup itself — the
 * same reasoning the identity service's address endpoints use. The only id in a path is a variant id,
 * which is a catalogue id, not a cart id.
 */
@RestController
@RequestMapping("/api/v1/carts/me")
@Tag(name = "Cart", description = "The signed-in customer's shopping cart")
class CartController {

    private final CartService cartService;
    private final CurrentUserProvider currentUserProvider;

    CartController(CartService cartService, CurrentUserProvider currentUserProvider) {
        this.cartService = cartService;
        this.currentUserProvider = currentUserProvider;
    }

    @GetMapping
    @Operation(summary = "Read the current cart, creating an empty one on first use")
    ResponseEntity<CartView> view() {
        AuthenticatedUser caller = currentUserProvider.requireUser();
        return ResponseEntity.ok(cartService.view(caller.userId()));
    }

    @PostMapping("/lines")
    @Operation(summary = "Add a variant to the cart, or increase its quantity if it is already there")
    ResponseEntity<CartView> addLine(@Valid @RequestBody CartLineRequest request) {
        AuthenticatedUser caller = currentUserProvider.requireUser();
        return ResponseEntity.ok(cartService.addLine(caller.userId(),
                new AddToCartCommand(request.variantId(), request.quantity())));
    }

    @PutMapping("/lines/{variantId}")
    @Operation(summary = "Set the quantity of a cart line to an exact value")
    ResponseEntity<CartView> updateLine(@PathVariable UUID variantId,
                                        @Valid @RequestBody CartQuantityRequest request) {
        AuthenticatedUser caller = currentUserProvider.requireUser();
        return ResponseEntity.ok(cartService.updateLine(caller.userId(),
                new UpdateCartLineCommand(variantId, request.quantity())));
    }

    @DeleteMapping("/lines/{variantId}")
    @Operation(summary = "Remove a line from the cart")
    ResponseEntity<CartView> removeLine(@PathVariable UUID variantId) {
        AuthenticatedUser caller = currentUserProvider.requireUser();
        return ResponseEntity.ok(cartService.removeLine(caller.userId(), variantId));
    }

    @DeleteMapping
    @Operation(summary = "Empty the cart")
    ResponseEntity<CartView> clear() {
        AuthenticatedUser caller = currentUserProvider.requireUser();
        return ResponseEntity.ok(cartService.clear(caller.userId()));
    }
}
