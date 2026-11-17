package com.choco.fulfilment.adapter.in.web;

import com.choco.core.page.PageResult;
import com.choco.core.port.PageQuery;
import com.choco.fulfilment.application.dto.DeliveryView;
import com.choco.fulfilment.application.dto.ProductionJobView;
import com.choco.fulfilment.application.service.DeliveryService;
import com.choco.fulfilment.application.service.ProductionQueryService;
import com.choco.security.principal.AuthenticatedUser;
import com.choco.security.principal.CurrentUserProvider;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * A customer's own view of their order's preparation and delivery progress.
 *
 * <p>Everything is nested under {@code /me} and the customer id always comes from the token, so there
 * is no id in the URL to tamper with, and every query is additionally scoped to the caller's own
 * customer id — a guessed order id belonging to somebody else is not found rather than returned.
 *
 * <p>What is shown is exactly what the seller recorded. There is no courier and no live position, so
 * the progress the customer sees is the steps the seller actually logged, with the times they logged
 * them. Inventing intermediate positions would tell the customer something nobody said.
 */
@RestController
@RequestMapping("/api/v1/fulfilment/me")
@Tag(name = "Fulfilment", description = "A customer's own preparation and delivery progress")
class CustomerFulfilmentController {

    private final ProductionQueryService productionQueryService;
    private final DeliveryService deliveryService;
    private final CurrentUserProvider currentUserProvider;

    CustomerFulfilmentController(ProductionQueryService productionQueryService,
                                 DeliveryService deliveryService,
                                 CurrentUserProvider currentUserProvider) {
        this.productionQueryService = productionQueryService;
        this.deliveryService = deliveryService;
        this.currentUserProvider = currentUserProvider;
    }

    @GetMapping("/orders/{orderId}/preparation")
    @Operation(summary = "The preparation progress of one of the signed-in customer's orders")
    ResponseEntity<List<ProductionJobView>> preparation(@PathVariable UUID orderId) {
        AuthenticatedUser caller = currentUserProvider.requireUser();
        return ResponseEntity.ok(
                productionQueryService.forCustomerOrder(caller.userId(), orderId));
    }

    @GetMapping("/orders/{orderId}/delivery")
    @Operation(summary = "The delivery progress of one of the signed-in customer's orders")
    ResponseEntity<DeliveryView> delivery(@PathVariable UUID orderId) {
        AuthenticatedUser caller = currentUserProvider.requireUser();
        return ResponseEntity.ok(deliveryService.forCustomerOrder(caller.userId(), orderId));
    }

    @GetMapping("/deliveries")
    @Operation(summary = "A page of the signed-in customer's deliveries, newest first")
    ResponseEntity<PageResult<DeliveryView>> deliveries(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) Boolean ascending) {
        AuthenticatedUser caller = currentUserProvider.requireUser();
        return ResponseEntity.ok(deliveryService.forCustomer(caller.userId(),
                PageQuery.of(page, size, sort, ascending)));
    }

    @GetMapping("/deliveries/{orderId}")
    @Operation(summary = "One of the signed-in customer's deliveries, by order id")
    ResponseEntity<DeliveryView> deliveryByOrder(@PathVariable UUID orderId) {
        AuthenticatedUser caller = currentUserProvider.requireUser();
        return ResponseEntity.ok(deliveryService.forCustomerOrder(caller.userId(), orderId));
    }
}
