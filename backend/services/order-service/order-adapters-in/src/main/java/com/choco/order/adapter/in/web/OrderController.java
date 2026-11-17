package com.choco.order.adapter.in.web;

import com.choco.core.page.PageResult;
import com.choco.core.port.PageQuery;
import com.choco.order.application.command.CancelOrderCommand;
import com.choco.order.application.command.RequestRefundCommand;
import com.choco.order.application.dto.OrderSummaryView;
import com.choco.order.application.dto.OrderView;
import com.choco.order.application.service.OrderCommandService;
import com.choco.order.application.service.OrderQueryService;
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
 * A customer's own orders.
 *
 * <p>Everything is nested under {@code /me} and the customer id always comes from the token, so there
 * is no id in the URL to tamper with. The query service additionally asserts ownership on a single
 * order lookup, so a guessed order id belonging to someone else is refused rather than returned.
 *
 * <p>Cancellation and refund requests are recorded as requests, not decisions. The seller reviews
 * them; the made-to-order policy means a change-of-mind request is not automatically granted, but it
 * is never silently discarded either.
 */
@RestController
@RequestMapping("/api/v1/orders/me")
@Tag(name = "Orders", description = "A customer's own orders, cancellation and refund requests")
class OrderController {

    private final OrderQueryService orderQueryService;
    private final OrderCommandService orderCommandService;
    private final CurrentUserProvider currentUserProvider;

    OrderController(OrderQueryService orderQueryService, OrderCommandService orderCommandService,
                    CurrentUserProvider currentUserProvider) {
        this.orderQueryService = orderQueryService;
        this.orderCommandService = orderCommandService;
        this.currentUserProvider = currentUserProvider;
    }

    @GetMapping
    @Operation(summary = "A page of the signed-in customer's orders, newest first")
    ResponseEntity<PageResult<OrderSummaryView>> history(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) Boolean ascending) {
        AuthenticatedUser caller = currentUserProvider.requireUser();
        return ResponseEntity.ok(orderQueryService.ownHistory(caller.userId(),
                PageQuery.of(page, size, sort, ascending)));
    }

    @GetMapping("/{orderId}")
    @Operation(summary = "Read one of the signed-in customer's orders, with its status history")
    ResponseEntity<OrderView> get(@PathVariable UUID orderId) {
        AuthenticatedUser caller = currentUserProvider.requireUser();
        return ResponseEntity.ok(orderQueryService.ownOrder(caller.userId(), orderId));
    }

    @PostMapping("/{orderId}/cancel")
    @Operation(summary = "Request cancellation of an order that has not yet left for delivery")
    ResponseEntity<OrderView> cancel(@PathVariable UUID orderId,
                                     @Valid @RequestBody CancelRequest request) {
        AuthenticatedUser caller = currentUserProvider.requireUser();
        return ResponseEntity.ok(orderCommandService.cancelOwnOrder(caller.userId(),
                new CancelOrderCommand(orderId, request.reason())));
    }

    @PostMapping("/{orderId}/refund-request")
    @Operation(summary = "Ask the seller to review a refund for this order")
    ResponseEntity<OrderView> requestRefund(@PathVariable UUID orderId,
                                            @Valid @RequestBody CancelRequest request) {
        AuthenticatedUser caller = currentUserProvider.requireUser();
        return ResponseEntity.ok(orderCommandService.requestRefund(caller.userId(),
                new RequestRefundCommand(orderId, request.reason())));
    }
}
