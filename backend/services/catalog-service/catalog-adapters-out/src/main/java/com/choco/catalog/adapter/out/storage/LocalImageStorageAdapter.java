package com.choco.catalog.adapter.out.storage;

import com.choco.catalog.application.port.ImageStoragePort;
import com.choco.core.error.ErrorCode;
import com.choco.core.error.NotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Locale;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Stores product images on a local volume.
 *
 * <p>A deliberate first implementation. The requirement is that the catalogue can hold photographs
 * without the platform depending on an external object store before one has been chosen. The
 * {@link ImageStoragePort} boundary means moving to S3 or similar later is an adapter swap, not a
 * change to any use case.
 *
 * <p>Two properties make this safe to run as-is:
 * <ul>
 *   <li><b>The key is generated here, never supplied.</b> It is a random UUID plus an extension
 *       derived from the validated content type. A caller cannot influence the path, so path traversal
 *       and overwriting another product's image are both impossible by construction.</li>
 *   <li><b>Reads are confined to the root.</b> The resolved path is checked to still sit under the
 *       configured root before it is read, so a key that somehow contained {@code ..} could not escape
 *       the directory.</li>
 * </ul>
 *
 * <p>The content type is stored alongside the bytes and served back with the file, so a browser is
 * told what it is receiving. Because only allow-listed image types reach here — the domain rejects the
 * rest before the bytes are stored — the response can never be an HTML or script payload.
 */
public class LocalImageStorageAdapter implements ImageStoragePort {

    private static final Logger log = LoggerFactory.getLogger(LocalImageStorageAdapter.class);

    private final Path root;

    public LocalImageStorageAdapter(Path root) {
        this.root = root.toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.root);
        } catch (IOException e) {
            throw new IllegalStateException("Could not create the image storage root at " + this.root, e);
        }
    }

    @Override
    public String store(UUID productId, String contentType, byte[] content) {
        String extension = extensionFor(contentType);
        // The product id names the directory, so an operator can see which files belong to which
        // product; the file name itself is random, so one upload cannot overwrite another.
        String key = productId + "/" + UUID.randomUUID() + extension;
        Path target = resolveWithinRoot(key);
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, content, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            return key;
        } catch (IOException e) {
            throw new IllegalStateException("Could not store the product image", e);
        }
    }

    @Override
    public StoredImage read(String storageKey) {
        Path target = resolveWithinRoot(storageKey);
        if (!Files.isRegularFile(target)) {
            throw new NotFoundException(ErrorCode.NOT_FOUND, "Image not found");
        }
        try {
            byte[] content = Files.readAllBytes(target);
            return new StoredImage(content, contentTypeFor(storageKey));
        } catch (IOException e) {
            throw new IllegalStateException("Could not read the product image", e);
        }
    }

    @Override
    public void delete(String storageKey) {
        Path target = resolveWithinRoot(storageKey);
        try {
            Files.deleteIfExists(target);
        } catch (IOException e) {
            // A failure to delete is logged but not propagated: the row referencing the image is
            // already gone, and an orphaned file is a housekeeping concern, not a request failure.
            log.warn("Could not delete stored image {}", storageKey, e);
        }
    }

    /**
     * Resolves a key against the root and refuses anything that escapes it.
     *
     * <p>The normalisation and the {@code startsWith} check together are what stop a crafted key from
     * reaching a file outside the image directory. The check is on the resolved, normalised path, so
     * {@code ..} segments cannot slip past a plain string comparison.
     */
    private Path resolveWithinRoot(String storageKey) {
        Path resolved = root.resolve(storageKey).normalize();
        if (!resolved.startsWith(root)) {
            throw new IllegalArgumentException("Invalid image key");
        }
        return resolved;
    }

    private static String extensionFor(String contentType) {
        return switch (contentType.toLowerCase(Locale.ROOT)) {
            case "image/jpeg" -> ".jpg";
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            default -> throw new IllegalArgumentException("Unsupported image type: " + contentType);
        };
    }

    private static String contentTypeFor(String storageKey) {
        String lower = storageKey.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".png")) {
            return "image/png";
        }
        if (lower.endsWith(".webp")) {
            return "image/webp";
        }
        return "image/jpeg";
    }
}
