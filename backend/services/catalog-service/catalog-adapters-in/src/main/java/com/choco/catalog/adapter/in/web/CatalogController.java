package com.choco.catalog.adapter.in.web;

import com.choco.catalog.application.dto.ProductSummaryView;
import com.choco.catalog.application.dto.ProductView;
import com.choco.catalog.application.service.ProductQueryService;
import com.choco.core.page.PageResult;
import com.choco.core.port.PageQuery;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The public catalogue.
 *
 * <p>Reachable without a token: a customer browses before signing in. The paths are declared public at
 * the gateway and in this service's own configuration, so the visibility rule is a deliberate, visible
 * edit rather than an inherited default.
 *
 * <p>Only published products are ever returned. That is enforced in the query service, not here, so a
 * future endpoint cannot forget it — but the endpoints are also written so that no parameter can widen
 * the result set to include a draft.
 */
@RestController
@RequestMapping("/api/v1/products")
@Tag(name = "Catalogue", description = "Public product catalogue")
class CatalogController {

    private final ProductQueryService queryService;

    CatalogController(ProductQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping
    @Operation(summary = "List published products")
    ResponseEntity<PageResult<ProductSummaryView>> list(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) Boolean ascending) {
        return ResponseEntity.ok(queryService.listVisible(search, PageQuery.of(page, size, sort, ascending)));
    }

    @GetMapping("/{slug}")
    @Operation(summary = "Read a published product by its address")
    ResponseEntity<ProductView> bySlug(@PathVariable String slug) {
        return ResponseEntity.ok(queryService.visibleBySlug(slug));
    }
}
