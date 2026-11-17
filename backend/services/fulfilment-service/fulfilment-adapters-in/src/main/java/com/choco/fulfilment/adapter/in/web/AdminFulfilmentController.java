package com.choco.fulfilment.adapter.in.web;

import com.choco.core.domain.Pincode;
import com.choco.core.page.PageResult;
import com.choco.core.port.PageQuery;
import com.choco.fulfilment.application.command.AddProductionNoteCommand;
import com.choco.fulfilment.application.command.AdjustStockCommand;
import com.choco.fulfilment.application.command.ConfigureRecipeCommand;
import com.choco.fulfilment.application.command.CreateIngredientCommand;
import com.choco.fulfilment.application.command.DeliveryAssignCommand;
import com.choco.fulfilment.application.command.DeliveryProgressCommand;
import com.choco.fulfilment.application.command.ProductionProgressCommand;
import com.choco.fulfilment.application.command.UpdateIngredientCommand;
import com.choco.fulfilment.application.command.UpsertServiceableAreaCommand;
import com.choco.fulfilment.application.dto.DeliveryCounters;
import com.choco.fulfilment.application.dto.DeliveryView;
import com.choco.fulfilment.application.dto.IngredientView;
import com.choco.fulfilment.application.dto.ProductionJobView;
import com.choco.fulfilment.application.dto.ProductionQueueCounters;
import com.choco.fulfilment.application.dto.RecipeView;
import com.choco.fulfilment.application.dto.ServiceableAreaView;
import com.choco.fulfilment.application.dto.StockLedgerEntryView;
import com.choco.fulfilment.application.service.DeliveryService;
import com.choco.fulfilment.application.service.InventoryService;
import com.choco.fulfilment.application.service.ProductionQueryService;
import com.choco.fulfilment.application.service.ProductionService;
import com.choco.fulfilment.domain.delivery.DeliveryStatus;
import com.choco.fulfilment.domain.production.ProductionStatus;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Fulfilment administration: the seller's kitchen, stock and delivery desk.
 *
 * <p>Each method states its own access rule next to the code it protects, split by permission rather
 * than lumped under one {@code ADMIN} check. Advancing preparation needs {@code production:write},
 * recording a delivery step needs {@code delivery:write}, and changing ingredients, recipes or covered
 * areas needs {@code settings:write}. A staff account trusted to pack chocolate is therefore not, by
 * default, also trusted to change the prices of delivery or the recipe the kitchen follows.
 *
 * <p>The acting administrator's id is read from the token and passed to the use case for the audit
 * trail; it is never taken from the request, so an administrator cannot record a change against
 * somebody else.
 */
@RestController
@RequestMapping("/api/v1/admin/fulfilment")
@PreAuthorize("hasAnyRole('" + Roles.ADMIN + "', '" + Roles.SUPER_ADMIN + "')")
@Tag(name = "Fulfilment administration",
        description = "Made-to-order preparation, stock, and seller-managed delivery")
class AdminFulfilmentController {

    private final ProductionService productionService;
    private final ProductionQueryService productionQueryService;
    private final InventoryService inventoryService;
    private final DeliveryService deliveryService;
    private final CurrentUserProvider currentUserProvider;

    AdminFulfilmentController(ProductionService productionService,
                              ProductionQueryService productionQueryService,
                              InventoryService inventoryService, DeliveryService deliveryService,
                              CurrentUserProvider currentUserProvider) {
        this.productionService = productionService;
        this.productionQueryService = productionQueryService;
        this.inventoryService = inventoryService;
        this.deliveryService = deliveryService;
        this.currentUserProvider = currentUserProvider;
    }

    // ---------------------------------------------------------------------------------------------
    // Production
    // ---------------------------------------------------------------------------------------------

    @GetMapping("/production/jobs")
    @Operation(summary = "The preparation queue, optionally filtered by status, order number or variant")
    ResponseEntity<PageResult<ProductionJobView>> productionQueue(
            @RequestParam(required = false) List<String> status,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) Boolean ascending) {
        return ResponseEntity.ok(productionQueryService.queue(parseProductionStatuses(status), search,
                PageQuery.of(page, size, sort, ascending)));
    }

    @GetMapping("/production/counters")
    @Operation(summary = "Kitchen workload counters for the dashboard")
    ResponseEntity<ProductionQueueCounters> productionCounters() {
        return ResponseEntity.ok(productionQueryService.counters());
    }

    @GetMapping("/production/jobs/{jobId}")
    @Operation(summary = "Read one preparation job")
    ResponseEntity<ProductionJobView> productionJob(@PathVariable UUID jobId) {
        return ResponseEntity.ok(productionQueryService.get(jobId));
    }

    @GetMapping("/production/orders/{orderId}")
    @Operation(summary = "Every preparation job for one order")
    ResponseEntity<List<ProductionJobView>> productionForOrder(@PathVariable UUID orderId) {
        return ResponseEntity.ok(productionQueryService.forOrder(orderId));
    }

    @PostMapping("/production/jobs/{jobId}/progress")
    @PreAuthorize("hasAuthority('" + Roles.PERM_PRODUCTION_WRITE + "')")
    @Operation(summary = "Advance preparation: acknowledge, start, hold, resume, complete, package, ready")
    ResponseEntity<ProductionJobView> advanceProduction(
            @PathVariable UUID jobId,
            @Valid @RequestBody AdminFulfilmentRequests.ProductionRequest request) {
        AuthenticatedUser caller = currentUserProvider.requireUser();
        return ResponseEntity.ok(productionService.advance(caller.userId().toString(),
                new ProductionProgressCommand(jobId, request.action(), request.reason())));
    }

    @PostMapping("/production/jobs/{jobId}/notes")
    @PreAuthorize("hasAuthority('" + Roles.PERM_PRODUCTION_WRITE + "')")
    @Operation(summary = "Attach a note to a preparation job")
    ResponseEntity<ProductionJobView> addProductionNote(
            @PathVariable UUID jobId,
            @Valid @RequestBody AdminFulfilmentRequests.ProductionNoteRequest request) {
        AuthenticatedUser caller = currentUserProvider.requireUser();
        return ResponseEntity.ok(productionService.addNote(caller.userId().toString(),
                new AddProductionNoteCommand(jobId, request.text())));
    }

    // ---------------------------------------------------------------------------------------------
    // Inventory
    // ---------------------------------------------------------------------------------------------

    @GetMapping("/ingredients")
    @Operation(summary = "The seller's ingredients and packaging, with low-stock flags")
    ResponseEntity<PageResult<IngredientView>> ingredients(
            @RequestParam(required = false, defaultValue = "false") boolean includeInactive,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) Boolean ascending) {
        return ResponseEntity.ok(inventoryService.listIngredients(includeInactive, search,
                PageQuery.of(page, size, sort, ascending)));
    }

    @GetMapping("/ingredients/low-stock")
    @Operation(summary = "Ingredients at or below their minimum level")
    ResponseEntity<List<IngredientView>> lowStock() {
        return ResponseEntity.ok(inventoryService.lowStock());
    }

    @PostMapping("/ingredients")
    @PreAuthorize("hasAuthority('" + Roles.PERM_SETTINGS_WRITE + "')")
    @Operation(summary = "Add a raw material or packaging item to the tracked stock")
    ResponseEntity<IngredientView> createIngredient(
            @Valid @RequestBody AdminFulfilmentRequests.CreateIngredientRequest request) {
        AuthenticatedUser caller = currentUserProvider.requireUser();
        return ResponseEntity.ok(inventoryService.create(caller.userId().toString(),
                new CreateIngredientCommand(request.name(), request.unit(), request.openingLevel(),
                        request.minimumLevel())));
    }

    @PutMapping("/ingredients/{ingredientId}")
    @PreAuthorize("hasAuthority('" + Roles.PERM_SETTINGS_WRITE + "')")
    @Operation(summary = "Change an ingredient's low-stock threshold, or retire it")
    ResponseEntity<IngredientView> updateIngredient(
            @PathVariable UUID ingredientId,
            @Valid @RequestBody AdminFulfilmentRequests.UpdateIngredientRequest request) {
        AuthenticatedUser caller = currentUserProvider.requireUser();
        return ResponseEntity.ok(inventoryService.update(caller.userId().toString(),
                new UpdateIngredientCommand(ingredientId, request.minimumLevel(), request.active())));
    }

    @PostMapping("/ingredients/{ingredientId}/stock")
    @PreAuthorize("hasAuthority('" + Roles.PERM_SETTINGS_WRITE + "')")
    @Operation(summary = "Record a manual stock movement: received, corrected or written off")
    ResponseEntity<IngredientView> adjustStock(
            @PathVariable UUID ingredientId,
            @Valid @RequestBody AdminFulfilmentRequests.AdjustStockRequest request) {
        AuthenticatedUser caller = currentUserProvider.requireUser();
        return ResponseEntity.ok(inventoryService.adjustStock(caller.userId().toString(),
                new AdjustStockCommand(ingredientId, request.amount(), request.reason(),
                        request.note())));
    }

    @GetMapping("/ingredients/{ingredientId}/ledger")
    @Operation(summary = "An ingredient's stock movements, newest first")
    ResponseEntity<PageResult<StockLedgerEntryView>> ledger(
            @PathVariable UUID ingredientId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        return ResponseEntity.ok(inventoryService.ledger(ingredientId, PageQuery.of(page, size, null, null)));
    }

    @GetMapping("/recipes/{variantId}")
    @Operation(summary = "A variant's recipe")
    ResponseEntity<RecipeView> recipe(@PathVariable UUID variantId) {
        return ResponseEntity.ok(inventoryService.recipe(variantId));
    }

    @PutMapping("/recipes/{variantId}")
    @PreAuthorize("hasAuthority('" + Roles.PERM_SETTINGS_WRITE + "')")
    @Operation(summary = "Set which ingredients one unit of a variant consumes")
    ResponseEntity<RecipeView> configureRecipe(
            @PathVariable UUID variantId,
            @Valid @RequestBody AdminFulfilmentRequests.ConfigureRecipeRequest request) {
        AuthenticatedUser caller = currentUserProvider.requireUser();
        List<ConfigureRecipeCommand.Line> lines = request.lines().stream()
                .map(line -> new ConfigureRecipeCommand.Line(line.ingredientId(), line.perUnit()))
                .toList();
        return ResponseEntity.ok(inventoryService.configureRecipe(caller.userId().toString(),
                new ConfigureRecipeCommand(variantId, lines)));
    }

    // ---------------------------------------------------------------------------------------------
    // Delivery
    // ---------------------------------------------------------------------------------------------

    @GetMapping("/deliveries")
    @Operation(summary = "Search deliveries by status, order number or recipient")
    ResponseEntity<PageResult<DeliveryView>> deliveries(
            @RequestParam(required = false) List<String> status,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) Boolean ascending) {
        return ResponseEntity.ok(deliveryService.search(parseDeliveryStatuses(status), search,
                PageQuery.of(page, size, sort, ascending)));
    }

    @GetMapping("/deliveries/counters")
    @Operation(summary = "Delivery board counters for the dashboard")
    ResponseEntity<DeliveryCounters> deliveryCounters() {
        return ResponseEntity.ok(deliveryService.counters());
    }

    @GetMapping("/deliveries/orders/{orderId}")
    @Operation(summary = "The delivery for one order, with its recorded attempts")
    ResponseEntity<DeliveryView> deliveryForOrder(@PathVariable UUID orderId) {
        return ResponseEntity.ok(deliveryService.forOrder(orderId));
    }

    @PostMapping("/deliveries/orders/{orderId}/assign")
    @PreAuthorize("hasAuthority('" + Roles.PERM_DELIVERY_WRITE + "')")
    @Operation(summary = "Assign delivery responsibility to a named person")
    ResponseEntity<DeliveryView> assignDelivery(
            @PathVariable UUID orderId,
            @Valid @RequestBody AdminFulfilmentRequests.AssignDeliveryRequest request) {
        AuthenticatedUser caller = currentUserProvider.requireUser();
        return ResponseEntity.ok(deliveryService.assign(caller.userId().toString(),
                new DeliveryAssignCommand(orderId, request.deliveryPersonName(),
                        request.deliveryPersonPhone())));
    }

    @PostMapping("/deliveries/orders/{orderId}/progress")
    @PreAuthorize("hasAuthority('" + Roles.PERM_DELIVERY_WRITE + "')")
    @Operation(summary = "Record a delivery step: out for delivery, delivered, failed, rescheduled, returned")
    ResponseEntity<DeliveryView> advanceDelivery(
            @PathVariable UUID orderId,
            @Valid @RequestBody AdminFulfilmentRequests.DeliveryProgressRequest request) {
        AuthenticatedUser caller = currentUserProvider.requireUser();
        return ResponseEntity.ok(deliveryService.advance(caller.userId().toString(),
                new DeliveryProgressCommand(orderId, request.action(), request.reason(),
                        request.proofReference(), request.notes())));
    }

    @GetMapping("/serviceable-areas")
    @Operation(summary = "The PIN codes the seller delivers to, and their charges")
    ResponseEntity<PageResult<ServiceableAreaView>> serviceableAreas(
            @RequestParam(required = false, defaultValue = "false") boolean includeInactive,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) Boolean ascending) {
        return ResponseEntity.ok(deliveryService.listAreas(includeInactive, search,
                PageQuery.of(page, size, sort, ascending)));
    }

    @PostMapping("/serviceable-areas")
    @PreAuthorize("hasAuthority('" + Roles.PERM_SETTINGS_WRITE + "')")
    @Operation(summary = "Create a delivery area the seller covers")
    ResponseEntity<ServiceableAreaView> createServiceableArea(
            @Valid @RequestBody AdminFulfilmentRequests.ServiceableAreaRequest request) {
        AuthenticatedUser caller = currentUserProvider.requireUser();
        return ResponseEntity.ok(deliveryService.upsertArea(caller.userId().toString(),
                new UpsertServiceableAreaCommand(null, request.pincodePrefix(), request.areaName(),
                        request.deliveryChargeMinorUnits(), request.active())));
    }

    @PutMapping("/serviceable-areas/{areaId}")
    @PreAuthorize("hasAuthority('" + Roles.PERM_SETTINGS_WRITE + "')")
    @Operation(summary = "Update a delivery area's name, charge or active flag")
    ResponseEntity<ServiceableAreaView> updateServiceableArea(
            @PathVariable UUID areaId,
            @Valid @RequestBody AdminFulfilmentRequests.ServiceableAreaRequest request) {
        AuthenticatedUser caller = currentUserProvider.requireUser();
        return ResponseEntity.ok(deliveryService.upsertArea(caller.userId().toString(),
                new UpsertServiceableAreaCommand(areaId, request.pincodePrefix(), request.areaName(),
                        request.deliveryChargeMinorUnits(), request.active())));
    }

    @GetMapping("/serviceability/{pincode}")
    @Operation(summary = "Whether the seller delivers to a PIN code, and at what charge")
    ResponseEntity<com.choco.fulfilment.application.dto.ServiceabilityQuoteView> serviceability(
            @PathVariable String pincode) {
        return ResponseEntity.ok(deliveryService.quote(Pincode.of(pincode)));
    }

    // ---------------------------------------------------------------------------------------------

    /**
     * Parses the production {@code status} filter values.
     *
     * <p>An unknown value is rejected rather than ignored: silently dropping it would return every job
     * for a mistyped filter, which on a kitchen screen reads as work that is not there.
     */
    private static List<ProductionStatus> parseProductionStatuses(List<String> statuses) {
        if (statuses == null || statuses.isEmpty()) {
            return List.of();
        }
        return statuses.stream().map(value -> {
            try {
                return ProductionStatus.valueOf(value.trim().toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new com.choco.core.error.ValidationException(
                        "Unknown production status: " + value);
            }
        }).toList();
    }

    private static List<DeliveryStatus> parseDeliveryStatuses(List<String> statuses) {
        if (statuses == null || statuses.isEmpty()) {
            return List.of();
        }
        return statuses.stream().map(value -> {
            try {
                return DeliveryStatus.valueOf(value.trim().toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new com.choco.core.error.ValidationException("Unknown delivery status: " + value);
            }
        }).toList();
    }
}
