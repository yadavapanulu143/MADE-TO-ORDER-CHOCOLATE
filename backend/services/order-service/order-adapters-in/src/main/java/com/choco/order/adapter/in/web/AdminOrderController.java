package com.choco.order.adapter.in.web;

import com.choco.core.page.PageResult;
import com.choco.core.port.PageQuery;
import com.choco.order.application.command.AddOrderNoteCommand;
import com.choco.order.application.command.AssignDeliveryCommand;
import com.choco.order.application.command.CancelOrderCommand;
import com.choco.order.application.command.DeliveryProgressCommand;
import com.choco.order.application.command.PreparationProgressCommand;
import com.choco.order.application.dto.OrderSummaryView;
import com.choco.order.application.dto.OrderView;
import com.choco.order.application.service.OrderCommandService;
import com.choco.order.application.service.OrderQueryService;
import com.choco.order.domain.order.OrderStatus;
import com.choco.security.principal.AuthenticatedUser;
import com.choco.security.principal.CurrentUserProvider;
import com.choco.security.principal.Roles;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
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
 * Order administration: the seller's view of every order and the made-to-order lifecycle.
 *
 * <p>Each method states its own access rule next to the code it protects, and the rules are split by
 * permission rather than lumped under one {@code ADMIN} check. Reading orders needs only an
 * authenticated administrator; advancing preparation needs {@code production:write}, moving an order
 * through delivery needs {@code delivery:write}, and cancelling needs {@code order:write}. A staff
 * account trusted to pack chocolate is therefore not, by default, also trusted to cancel an order.
 *
 * <p>The acting administrator's id is read from the token and passed to the use case for the audit
 * trail; it is never taken from the request, so an administrator cannot record a change against
 * somebody else.
 */
@RestController
@RequestMapping("/api/v1/admin/orders")
@PreAuthorize("hasAnyRole('" + Roles.ADMIN + "', '" + Roles.SUPER_ADMIN + "')")
@Tag(name = "Order administration", description = "The seller's order, production and delivery views")
class AdminOrderController {

    private final OrderQueryService orderQueryService;
    private final OrderCommandService orderCommandService;
    private final CurrentUserProvider currentUserProvider;

    AdminOrderController(OrderQueryService orderQueryService, OrderCommandService orderCommandService,
                         CurrentUserProvider currentUserProvider) {
        this.orderQueryService = orderQueryService;
        this.orderCommandService = orderCommandService;
        this.currentUserProvider = currentUserProvider;
    }

    @GetMapping
    @Operation(summary = "Search orders by status, customer or order number")
    ResponseEntity<PageResult<OrderSummaryView>> search(
            @RequestParam(required = false) List<String> status,
            @RequestParam(required = false) UUID customerId,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) Boolean ascending) {
        return ResponseEntity.ok(orderQueryService.searchForAdmin(parseStatuses(status), customerId, search,
                PageQuery.of(page, size, sort, ascending)));
    }

    @GetMapping("/counters")
    @Operation(summary = "Workload counters for the dashboard, by lifecycle stage")
    ResponseEntity<OrderQueryService.OrderCounters> counters() {
        return ResponseEntity.ok(orderQueryService.counters());
    }

    @GetMapping("/{orderId}")
    @Operation(summary = "Read a complete order, in any status")
    ResponseEntity<OrderView> get(@PathVariable UUID orderId) {
        return ResponseEntity.ok(orderQueryService.forAdmin(orderId));
    }

    @PostMapping("/{orderId}/preparation")
    @PreAuthorize("hasAuthority('" + Roles.PERM_PRODUCTION_WRITE + "')")
    @Operation(summary = "Advance made-to-order preparation: start, hold, resume, complete, package, ready")
    ResponseEntity<OrderView> preparation(@PathVariable UUID orderId,
                                          @Valid @RequestBody AdminOrderRequests.PreparationRequest request) {
        AuthenticatedUser caller = currentUserProvider.requireUser();
        return ResponseEntity.ok(orderCommandService.advancePreparation(caller.userId().toString(),
                new PreparationProgressCommand(orderId, request.action(), request.reason())));
    }

    @PostMapping("/{orderId}/delivery/assign")
    @PreAuthorize("hasAuthority('" + Roles.PERM_DELIVERY_WRITE + "')")
    @Operation(summary = "Assign delivery responsibility to a named person")
    ResponseEntity<OrderView> assignDelivery(
            @PathVariable UUID orderId,
            @Valid @RequestBody AdminOrderRequests.AssignDeliveryRequest request) {
        AuthenticatedUser caller = currentUserProvider.requireUser();
        return ResponseEntity.ok(orderCommandService.assignDelivery(caller.userId().toString(),
                new AssignDeliveryCommand(orderId, request.deliveryPersonName(),
                        request.deliveryPersonPhone())));
    }

    @PostMapping("/{orderId}/delivery")
    @PreAuthorize("hasAuthority('" + Roles.PERM_DELIVERY_WRITE + "')")
    @Operation(summary = "Record a delivery step: out for delivery, delivered, failed, rescheduled, returned")
    ResponseEntity<OrderView> delivery(@PathVariable UUID orderId,
                                       @Valid @RequestBody AdminOrderRequests.DeliveryRequest request) {
        AuthenticatedUser caller = currentUserProvider.requireUser();
        return ResponseEntity.ok(orderCommandService.advanceDelivery(caller.userId().toString(),
                new DeliveryProgressCommand(orderId, request.action(), request.reason())));
    }

    @PostMapping("/{orderId}/notes")
    @PreAuthorize("hasAuthority('" + Roles.PERM_ORDER_WRITE + "')")
    @Operation(summary = "Attach a note to an order")
    ResponseEntity<OrderView> addNote(@PathVariable UUID orderId,
                                      @Valid @RequestBody AdminOrderRequests.NoteRequest request) {
        AuthenticatedUser caller = currentUserProvider.requireUser();
        return ResponseEntity.ok(orderCommandService.addNote(caller.userId().toString(),
                new AddOrderNoteCommand(orderId, request.text())));
    }

    @PostMapping("/{orderId}/cancel")
    @PreAuthorize("hasAuthority('" + Roles.PERM_ORDER_WRITE + "')")
    @Operation(summary = "Cancel an order that cannot be fulfilled")
    ResponseEntity<OrderView> cancel(@PathVariable UUID orderId,
                                     @Valid @RequestBody AdminOrderRequests.CancelRequest request) {
        AuthenticatedUser caller = currentUserProvider.requireUser();
        return ResponseEntity.ok(orderCommandService.cancelAsAdmin(caller.userId().toString(),
                new CancelOrderCommand(orderId, request.reason())));
    }

    @PostMapping("/{orderId}/payment-retry")
    @PreAuthorize("hasAuthority('" + Roles.PERM_ORDER_WRITE + "')")
    @Operation(summary = "Return a failed order to pending so the customer may pay again")
    ResponseEntity<OrderView> allowPaymentRetry(@PathVariable UUID orderId) {
        AuthenticatedUser caller = currentUserProvider.requireUser();
        return ResponseEntity.ok(orderCommandService.allowPaymentRetry(caller.userId().toString(), orderId));
    }

    /**
     * Parses the {@code status} filter values.
     *
     * <p>An unknown value is rejected rather than ignored: silently dropping it would return every
     * order for a mistyped filter, which on a dashboard reads as "there are no orders of that kind"
     * only if it happened to return none — the opposite of safe.
     */
    private static List<OrderStatus> parseStatuses(List<String> statuses) {
        if (statuses == null || statuses.isEmpty()) {
            return List.of();
        }
        return statuses.stream()
                .map(value -> {
                    try {
                        return OrderStatus.valueOf(value.trim().toUpperCase());
                    } catch (IllegalArgumentException e) {
                        throw new com.choco.core.error.ValidationException("Unknown order status: " + value);
                    }
                })
                .toList();
    }
}
