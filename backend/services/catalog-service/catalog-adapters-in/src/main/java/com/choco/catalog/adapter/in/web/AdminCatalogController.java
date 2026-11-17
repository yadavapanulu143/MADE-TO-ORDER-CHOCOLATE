package com.choco.catalog.adapter.in.web;

import com.choco.catalog.application.command.AddAttributeCommand;
import com.choco.catalog.application.command.AddVariantCommand;
import com.choco.catalog.application.command.CreateProductCommand;
import com.choco.catalog.application.command.UpdateImageCommand;
import com.choco.catalog.application.command.UpdateProductCommand;
import com.choco.catalog.application.command.UpdateVariantCommand;
import com.choco.catalog.application.dto.ProductSummaryView;
import com.choco.catalog.application.dto.ProductView;
import com.choco.catalog.application.service.ProductCatalogService;
import com.choco.catalog.application.service.ProductQueryService;
import com.choco.core.page.PageResult;
import com.choco.core.port.PageQuery;
import com.choco.security.principal.AuthenticatedUser;
import com.choco.security.principal.CurrentUserProvider;
import com.choco.security.principal.Roles;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Catalogue administration.
 *
 * <p>Every method requires the {@code product:write} permission, checked by {@code @PreAuthorize}
 * rather than by the URL pattern alone. Declaring the rule on the method keeps it next to the code it
 * protects, so a new endpoint cannot be added without a visible access decision, and a permission can
 * be granted to a role without redeploying this service.
 *
 * <p>The acting administrator's id is read from the token and passed to the use case for the audit
 * trail. It is never taken from the request, so an administrator cannot record a change against
 * somebody else.
 */
@RestController
@RequestMapping("/api/v1/admin/catalog")
@PreAuthorize("hasAuthority('" + Roles.PERM_PRODUCT_WRITE + "')")
@Tag(name = "Catalogue administration", description = "Manage products, variants, images and settings")
class AdminCatalogController {

    private final ProductCatalogService catalogService;
    private final ProductQueryService queryService;
    private final CurrentUserProvider currentUserProvider;

    AdminCatalogController(ProductCatalogService catalogService,
                           ProductQueryService queryService,
                           CurrentUserProvider currentUserProvider) {
        this.catalogService = catalogService;
        this.queryService = queryService;
        this.currentUserProvider = currentUserProvider;
    }

    @GetMapping("/products")
    @Operation(summary = "List products for administration, in any status")
    ResponseEntity<PageResult<ProductSummaryView>> list(
            @RequestParam(required = false) List<String> status,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) Boolean ascending) {
        return ResponseEntity.ok(queryService.listForAdmin(status, search,
                PageQuery.of(page, size, sort, ascending)));
    }

    @GetMapping("/products/{productId}")
    @Operation(summary = "Read a product for administration")
    ResponseEntity<ProductView> get(@PathVariable UUID productId) {
        return ResponseEntity.ok(queryService.forAdmin(productId));
    }

    @PostMapping("/products")
    @Operation(summary = "Create a product")
    ResponseEntity<ProductView> create(@Valid @RequestBody ProductRequest request) {
        AuthenticatedUser caller = currentUserProvider.requireUser();
        ProductView created = catalogService.create(caller.userId(), toCommand(request));
        return ResponseEntity.created(URI.create("/api/v1/admin/catalog/products/" + created.id()))
                .body(created);
    }

    @PutMapping("/products/{productId}")
    @Operation(summary = "Edit a product")
    ResponseEntity<ProductView> update(@PathVariable UUID productId,
                                       @Valid @RequestBody ProductRequest request) {
        AuthenticatedUser caller = currentUserProvider.requireUser();
        return ResponseEntity.ok(catalogService.update(caller.userId(), productId,
                new UpdateProductCommand(request.name(), request.shortDescription(),
                        request.description(), request.preparationInfo(), request.deliveryInfo())));
    }

    @PostMapping("/products/{productId}/publish")
    @Operation(summary = "Publish a product to the storefront")
    ResponseEntity<ProductView> publish(@PathVariable UUID productId) {
        AuthenticatedUser caller = currentUserProvider.requireUser();
        return ResponseEntity.ok(catalogService.publish(caller.userId(), productId));
    }

    @PostMapping("/products/{productId}/unpublish")
    @Operation(summary = "Remove a product from the storefront")
    ResponseEntity<ProductView> unpublish(@PathVariable UUID productId) {
        AuthenticatedUser caller = currentUserProvider.requireUser();
        return ResponseEntity.ok(catalogService.unpublish(caller.userId(), productId));
    }

    @PostMapping("/products/{productId}/archive")
    @Operation(summary = "Archive a product")
    ResponseEntity<ProductView> archive(@PathVariable UUID productId) {
        AuthenticatedUser caller = currentUserProvider.requireUser();
        return ResponseEntity.ok(catalogService.archive(caller.userId(), productId));
    }

    // ---------------------------------------------------------------------------------------------
    // Variants
    // ---------------------------------------------------------------------------------------------

    @PostMapping("/products/{productId}/variants")
    @Operation(summary = "Add a variant")
    ResponseEntity<ProductView> addVariant(@PathVariable UUID productId,
                                           @Valid @RequestBody VariantRequest request) {
        AuthenticatedUser caller = currentUserProvider.requireUser();
        return ResponseEntity.ok(catalogService.addVariant(caller.userId(), productId,
                new AddVariantCommand(request.label(), request.sku(), request.price(),
                        request.minOrderQuantity(), request.maxOrderQuantity())));
    }

    @PutMapping("/products/{productId}/variants/{variantId}")
    @Operation(summary = "Edit a variant")
    ResponseEntity<ProductView> updateVariant(@PathVariable UUID productId,
                                              @PathVariable UUID variantId,
                                              @Valid @RequestBody VariantRequest request) {
        AuthenticatedUser caller = currentUserProvider.requireUser();
        return ResponseEntity.ok(catalogService.updateVariant(caller.userId(), productId, variantId,
                new UpdateVariantCommand(request.label(), request.sku(), request.price(),
                        request.minOrderQuantity(), request.maxOrderQuantity())));
    }

    @DeleteMapping("/products/{productId}/variants/{variantId}")
    @Operation(summary = "Remove a variant")
    ResponseEntity<ProductView> removeVariant(@PathVariable UUID productId,
                                              @PathVariable UUID variantId) {
        AuthenticatedUser caller = currentUserProvider.requireUser();
        return ResponseEntity.ok(catalogService.removeVariant(caller.userId(), productId, variantId));
    }

    // ---------------------------------------------------------------------------------------------
    // Images
    // ---------------------------------------------------------------------------------------------

    @PostMapping(path = "/products/{productId}/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload a product image")
    ResponseEntity<ProductView> uploadImage(@PathVariable UUID productId,
                                            @RequestPart("file") MultipartFile file,
                                            @RequestParam(required = false) String altText) {
        AuthenticatedUser caller = currentUserProvider.requireUser();
        byte[] content;
        try {
            content = file.getBytes();
        } catch (java.io.IOException e) {
            throw new com.choco.core.error.ValidationException(com.choco.core.error.ErrorCode.VALIDATION_FAILED,
                    "Could not read the uploaded file");
        }
        return ResponseEntity.ok(catalogService.uploadImage(caller.userId(), productId,
                file.getContentType(), content.length, content, altText));
    }

    @PutMapping("/products/{productId}/images/{imageId}")
    @Operation(summary = "Edit an image's presentation metadata")
    ResponseEntity<ProductView> updateImage(@PathVariable UUID productId,
                                            @PathVariable UUID imageId,
                                            @Valid @RequestBody ImageUpdateRequest request) {
        AuthenticatedUser caller = currentUserProvider.requireUser();
        return ResponseEntity.ok(catalogService.updateImage(caller.userId(), productId, imageId,
                new UpdateImageCommand(request.altText(), request.displayOrder())));
    }

    @PostMapping("/products/{productId}/images/{imageId}/primary")
    @Operation(summary = "Make an image the product's primary image")
    ResponseEntity<ProductView> makePrimary(@PathVariable UUID productId,
                                            @PathVariable UUID imageId) {
        AuthenticatedUser caller = currentUserProvider.requireUser();
        return ResponseEntity.ok(catalogService.makeImagePrimary(caller.userId(), productId, imageId));
    }

    @DeleteMapping("/products/{productId}/images/{imageId}")
    @Operation(summary = "Remove an image")
    ResponseEntity<ProductView> removeImage(@PathVariable UUID productId,
                                            @PathVariable UUID imageId) {
        AuthenticatedUser caller = currentUserProvider.requireUser();
        return ResponseEntity.ok(catalogService.removeImage(caller.userId(), productId, imageId));
    }

    // ---------------------------------------------------------------------------------------------
    // Attributes
    // ---------------------------------------------------------------------------------------------

    @PostMapping("/products/{productId}/attributes")
    @Operation(summary = "Add a descriptive attribute")
    ResponseEntity<ProductView> addAttribute(@PathVariable UUID productId,
                                             @Valid @RequestBody AttributeRequest request) {
        AuthenticatedUser caller = currentUserProvider.requireUser();
        return ResponseEntity.ok(catalogService.addAttribute(caller.userId(), productId,
                new AddAttributeCommand(request.name(), request.value())));
    }

    @DeleteMapping("/products/{productId}/attributes/{attributeId}")
    @Operation(summary = "Remove a descriptive attribute")
    ResponseEntity<ProductView> removeAttribute(@PathVariable UUID productId,
                                                @PathVariable UUID attributeId) {
        AuthenticatedUser caller = currentUserProvider.requireUser();
        return ResponseEntity.ok(catalogService.removeAttribute(caller.userId(), productId, attributeId));
    }

    private static CreateProductCommand toCommand(ProductRequest request) {
        return new CreateProductCommand(request.name(), request.shortDescription(), request.description(),
                request.preparationInfo(), request.deliveryInfo());
    }
}
