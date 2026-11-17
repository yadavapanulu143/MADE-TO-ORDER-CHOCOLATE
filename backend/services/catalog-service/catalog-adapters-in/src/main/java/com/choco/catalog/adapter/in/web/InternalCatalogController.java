package com.choco.catalog.adapter.in.web;

import com.choco.catalog.application.dto.VariantLookupView;
import com.choco.catalog.application.service.ProductQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Internal catalogue reads for other services.
 *
 * <p>Separate from the public {@link CatalogController} because the caller is a service, not a
 * shopper: the request is a batch of variant ids rather than a slug, and the response carries the
 * owning product's name and availability so one call answers "what is this variant and may it be
 * bought". It is authenticated like every non-public path — the order service forwards the customer's
 * own token rather than holding a privileged credential of its own.
 *
 * <p>Only published products are ever resolved, so this endpoint can never expose a draft the
 * storefront would hide.
 */
@RestController
@RequestMapping("/api/v1/catalog/variants")
@Tag(name = "Catalogue (internal)", description = "Variant lookups for other services")
class InternalCatalogController {

    /** Upper bound on ids per call, so one request cannot ask for an unbounded result set. */
    private static final int MAX_IDS = 100;

    private final ProductQueryService queryService;

    InternalCatalogController(ProductQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping
    @Operation(summary = "Resolve variant ids to their current price and availability")
    ResponseEntity<Map<UUID, VariantLookupView>> resolve(@RequestParam("ids") List<UUID> ids) {
        Collection<UUID> requested = ids.size() > MAX_IDS ? ids.subList(0, MAX_IDS) : ids;
        return ResponseEntity.ok(queryService.resolveVariants(requested));
    }
}
