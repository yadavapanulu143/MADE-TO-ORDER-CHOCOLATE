package com.choco.catalog.adapter.in.web;

import com.choco.catalog.application.port.ImageStoragePort;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Duration;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Serves stored product images.
 *
 * <p>Images are streamed through the service rather than exposed as a static directory, so every read
 * goes through the same key validation and the same response headers. Serving the storage directory
 * directly would put the confinement check and the safety headers in a web server configuration that
 * a reviewer is much less likely to see.
 *
 * <p>The response carries {@code X-Content-Type-Options: nosniff} and an explicit content type, and is
 * marked inline. Together those mean a browser will not guess a different type for the bytes and will
 * not execute them as a document. Only allow-listed image types ever reach storage, so the type here
 * is one of three known values.
 *
 * <p>Content is cacheable for a long time because a stored key is unique and immutable: a new upload
 * produces a new key, so a cached image can never become stale.
 */
@RestController
@RequestMapping("/api/v1/catalog/images")
@Tag(name = "Catalogue images", description = "Product photographs")
class CatalogImageController {

    private final ImageStoragePort imageStorage;

    CatalogImageController(ImageStoragePort imageStorage) {
        this.imageStorage = imageStorage;
    }

    @GetMapping("/{*storageKey}")
    @Operation(summary = "Fetch a stored product image")
    ResponseEntity<byte[]> image(@PathVariable String storageKey) {
        // A leading slash is included by the catch-all pattern; strip it so the key matches what was
        // stored.
        String key = storageKey.startsWith("/") ? storageKey.substring(1) : storageKey;
        ImageStoragePort.StoredImage stored = imageStorage.read(key);

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(stored.contentType()))
                .header("X-Content-Type-Options", "nosniff")
                .header("Content-Disposition", "inline")
                .cacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable())
                .body(stored.content());
    }
}
