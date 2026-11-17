package com.choco.catalog.application.service;

import com.choco.catalog.application.command.AddAttributeCommand;
import com.choco.catalog.application.command.AddVariantCommand;
import com.choco.catalog.application.command.CreateProductCommand;
import com.choco.catalog.application.command.UpdateImageCommand;
import com.choco.catalog.application.command.UpdateProductCommand;
import com.choco.catalog.application.command.UpdateVariantCommand;
import com.choco.catalog.application.dto.ProductView;
import com.choco.catalog.application.port.AuditLogPort;
import com.choco.catalog.application.port.ImageStoragePort;
import com.choco.catalog.domain.product.Product;
import com.choco.catalog.domain.product.ProductStatus;
import com.choco.core.port.EventPublisher;
import com.choco.catalog.domain.port.ProductRepository;
import com.choco.core.domain.Money;
import com.choco.core.error.ConflictException;
import com.choco.core.error.ErrorCode;
import com.choco.core.error.NotFoundException;
import com.choco.core.error.ValidationException;
import com.choco.core.port.Clock;
import com.choco.core.port.IdGenerator;
import com.choco.core.port.TransactionRunner;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * The administrative write side of the catalogue: creating and editing products and everything they
 * own.
 *
 * <p>Every mutation follows the same shape, and the shape is the point. The aggregate is loaded, asked
 * to make the change (which enforces the invariants and records the domain events), saved, and its
 * events drained to the outbox — all inside one {@link TransactionRunner} unit of work. Writing the
 * row and publishing the event in the same transaction is what stops the two from diverging: either
 * both happen or neither does.
 *
 * <p>Audit is written after the transaction commits, so the record describes something that actually
 * happened. An audit row for a rolled-back change would be worse than none: it would tell an
 * investigator a price was changed when it was not.
 *
 * <p>Ownership of a product is not a concept here. There is one seller, so there is no per-seller
 * scoping; authorisation is by admin permission, enforced at the controller.
 */
public class ProductCatalogService {

    private final ProductRepository productRepository;
    private final EventPublisher eventPublisher;
    private final ImageStoragePort imageStorage;
    private final AuditLogPort auditLog;
    private final TransactionRunner transactionRunner;
    private final IdGenerator idGenerator;
    private final Clock clock;

    public ProductCatalogService(ProductRepository productRepository,
                                 EventPublisher eventPublisher,
                                 ImageStoragePort imageStorage,
                                 AuditLogPort auditLog,
                                 TransactionRunner transactionRunner,
                                 IdGenerator idGenerator,
                                 Clock clock) {
        this.productRepository = productRepository;
        this.eventPublisher = eventPublisher;
        this.imageStorage = imageStorage;
        this.auditLog = auditLog;
        this.transactionRunner = transactionRunner;
        this.idGenerator = idGenerator;
        this.clock = clock;
    }

    public ProductView create(UUID actorId, CreateProductCommand command) {
        Instant now = clock.now();
        Product product = Product.create(idGenerator.nextId(), command.name(), command.shortDescription(),
                command.description(), command.preparationInfo(), command.deliveryInfo(), now);

        // A slug is derived from the name. Two products with the same name would collide on a public
        // address, so the clash is refused here rather than producing a page that cannot be reached.
        if (productRepository.existsBySlug(product.slug())) {
            throw new ConflictException(ErrorCode.CONFLICT,
                    "A product with a similar name already exists");
        }

        persist(product, now);
        audit(actorId, "PRODUCT_CREATED", product.id(), null);
        return ProductViewMapper.toView(product);
    }

    public ProductView update(UUID actorId, UUID productId, UpdateProductCommand command) {
        Instant now = clock.now();
        Product product = require(productId);

        transactionRunner.execute(() -> {
            product.updateDetails(command.name(), command.shortDescription(), command.description(),
                    command.preparationInfo(), command.deliveryInfo(), now);
            productRepository.save(product);
            eventPublisher.publishAll(product.pullDomainEvents());
        });

        audit(actorId, "PRODUCT_UPDATED", productId, null);
        return ProductViewMapper.toView(product);
    }

    public ProductView addVariant(UUID actorId, UUID productId, AddVariantCommand command) {
        Instant now = clock.now();
        Product product = require(productId);
        Money price = toMoney(command.price());

        transactionRunner.execute(() -> {
            product.addVariant(idGenerator.nextId(), command.label(), command.sku(), price,
                    command.minOrderQuantity(), command.maxOrderQuantity(), now);
            productRepository.save(product);
            eventPublisher.publishAll(product.pullDomainEvents());
        });

        audit(actorId, "PRODUCT_VARIANT_ADDED", productId, null);
        return ProductViewMapper.toView(product);
    }

    public ProductView updateVariant(UUID actorId, UUID productId, UUID variantId,
                                     UpdateVariantCommand command) {
        Instant now = clock.now();
        Product product = require(productId);
        Money price = toMoney(command.price());

        transactionRunner.execute(() -> {
            product.updateVariant(variantId, command.label(), command.sku(), price,
                    command.minOrderQuantity(), command.maxOrderQuantity(), now);
            productRepository.save(product);
            eventPublisher.publishAll(product.pullDomainEvents());
        });

        audit(actorId, "PRODUCT_VARIANT_UPDATED", productId, variantId.toString());
        return ProductViewMapper.toView(product);
    }

    public ProductView removeVariant(UUID actorId, UUID productId, UUID variantId) {
        Instant now = clock.now();
        Product product = require(productId);

        transactionRunner.execute(() -> {
            product.removeVariant(variantId, now);
            productRepository.save(product);
        });

        audit(actorId, "PRODUCT_VARIANT_REMOVED", productId, variantId.toString());
        return ProductViewMapper.toView(product);
    }

    /**
     * Stores an uploaded image and attaches it to the product.
     *
     * <p>The declared type and size are validated before the bytes are stored, and the storage key is
     * generated by the storage adapter rather than taken from the upload. A caller therefore cannot
     * choose where their content lands, cannot attach a non-image, and cannot fill the volume with one
     * oversized file. The store happens before the row is written; if the row write then fails, the
     * object is orphaned but the storefront never shows a broken image, which is the failure a customer
     * would actually see.
     */
    public ProductView uploadImage(UUID actorId, UUID productId, String contentType, long sizeBytes,
                                   byte[] content, String altText) {
        Instant now = clock.now();
        Product product = require(productId);

        // Validate before storing anything, so a rejected upload leaves no object behind.
        com.choco.catalog.domain.product.ProductImage.assertAcceptableUpload(contentType, sizeBytes);

        String storageKey = imageStorage.store(productId, contentType, content);

        try {
            transactionRunner.execute(() -> {
                product.addImage(idGenerator.nextId(), storageKey, contentType, sizeBytes, altText, now);
                productRepository.save(product);
            });
        } catch (RuntimeException e) {
            // The row did not commit, so the object is unreachable. Remove it rather than leave it for
            // a sweep that may never run.
            imageStorage.delete(storageKey);
            throw e;
        }

        audit(actorId, "PRODUCT_IMAGE_UPLOADED", productId, storageKey);
        return ProductViewMapper.toView(product);
    }

    public ProductView updateImage(UUID actorId, UUID productId, UUID imageId, UpdateImageCommand command) {
        Instant now = clock.now();
        Product product = require(productId);

        transactionRunner.execute(() -> {
            product.updateImage(imageId, command.altText(), command.displayOrder(), now);
            productRepository.save(product);
        });

        audit(actorId, "PRODUCT_IMAGE_UPDATED", productId, imageId.toString());
        return ProductViewMapper.toView(product);
    }

    public ProductView makeImagePrimary(UUID actorId, UUID productId, UUID imageId) {
        Instant now = clock.now();
        Product product = require(productId);

        transactionRunner.execute(() -> {
            product.makeImagePrimary(imageId, now);
            productRepository.save(product);
        });

        audit(actorId, "PRODUCT_IMAGE_PRIMARY_CHANGED", productId, imageId.toString());
        return ProductViewMapper.toView(product);
    }

    /**
     * Removes an image from a product and deletes its stored bytes.
     *
     * <p>The bytes are deleted after the database change commits. Doing it the other way round would
     * leave a row pointing at content that no longer exists if the transaction then failed — a broken
     * image on the storefront, which is the more visible failure. An orphaned object from a failed
     * delete is the lesser problem and can be swept by a later job.
     */
    public ProductView removeImage(UUID actorId, UUID productId, UUID imageId) {
        Instant now = clock.now();
        Product product = require(productId);

        String storageKey = product.images().stream()
                .filter(image -> image.id().equals(imageId))
                .map(image -> image.storageKey())
                .findFirst()
                .orElseThrow(() -> new NotFoundException(ErrorCode.NOT_FOUND, "Image not found"));

        transactionRunner.execute(() -> {
            product.removeImage(imageId, now);
            productRepository.save(product);
        });

        imageStorage.delete(storageKey);
        audit(actorId, "PRODUCT_IMAGE_REMOVED", productId, imageId.toString());
        return ProductViewMapper.toView(product);
    }

    public ProductView addAttribute(UUID actorId, UUID productId, AddAttributeCommand command) {
        Instant now = clock.now();
        Product product = require(productId);

        transactionRunner.execute(() -> {
            product.addAttribute(idGenerator.nextId(), command.name(), command.value(), now);
            productRepository.save(product);
        });

        audit(actorId, "PRODUCT_ATTRIBUTE_ADDED", productId, null);
        return ProductViewMapper.toView(product);
    }

    public ProductView removeAttribute(UUID actorId, UUID productId, UUID attributeId) {
        Instant now = clock.now();
        Product product = require(productId);

        transactionRunner.execute(() -> {
            product.removeAttribute(attributeId, now);
            productRepository.save(product);
        });

        audit(actorId, "PRODUCT_ATTRIBUTE_REMOVED", productId, attributeId.toString());
        return ProductViewMapper.toView(product);
    }

    public ProductView publish(UUID actorId, UUID productId) {
        Instant now = clock.now();
        Product product = require(productId);

        transactionRunner.execute(() -> {
            product.publish(now);
            productRepository.save(product);
            eventPublisher.publishAll(product.pullDomainEvents());
        });

        audit(actorId, "PRODUCT_PUBLISHED", productId, null);
        return ProductViewMapper.toView(product);
    }

    public ProductView unpublish(UUID actorId, UUID productId) {
        Instant now = clock.now();
        Product product = require(productId);

        transactionRunner.execute(() -> {
            product.unpublish(now);
            productRepository.save(product);
            eventPublisher.publishAll(product.pullDomainEvents());
        });

        audit(actorId, "PRODUCT_UNPUBLISHED", productId, null);
        return ProductViewMapper.toView(product);
    }

    /**
     * Archives a product.
     *
     * <p>The product is not deleted. Historical orders reference its variants and their prices, and a
     * hard delete would either break those references or remove data the catalog service must keep for
     * the order service's history. Archiving removes it from the storefront and keeps the record.
     */
    public ProductView archive(UUID actorId, UUID productId) {
        Instant now = clock.now();
        Product product = require(productId);

        transactionRunner.execute(() -> {
            product.archive(now);
            productRepository.save(product);
            eventPublisher.publishAll(product.pullDomainEvents());
        });

        audit(actorId, "PRODUCT_ARCHIVED", productId, null);
        return ProductViewMapper.toView(product);
    }

    /** Loads a product for administration, in any status. Reports "not found" rather than revealing ids. */
    Product require(UUID productId) {
        return productRepository.findById(productId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.NOT_FOUND, "Product not found"));
    }

    private void persist(Product product, Instant now) {
        transactionRunner.execute(() -> {
            productRepository.save(product);
            eventPublisher.publishAll(product.pullDomainEvents());
        });
    }

    /**
     * Converts a decimal rupee amount to exact minor units.
     *
     * <p>A negative or zero price is refused here as well as in the domain, so the error a caller sees
     * names the field rather than surfacing a value-object message.
     */
    private static Money toMoney(BigDecimal price) {
        if (price == null) {
            throw new ValidationException(ErrorCode.VALIDATION_FAILED, "A price is required");
        }
        if (price.signum() <= 0) {
            throw new ValidationException(ErrorCode.VALIDATION_FAILED, "A price must be greater than zero");
        }
        try {
            return Money.of(price, Money.INR);
        } catch (ArithmeticException | IllegalArgumentException e) {
            throw new ValidationException(ErrorCode.VALIDATION_FAILED, "Enter a valid price");
        }
    }

    private void audit(UUID actorId, String action, UUID productId, String detail) {
        auditLog.record("ADMIN", actorId.toString(), action, "PRODUCT", productId.toString(),
                "SUCCESS", detail, null);
    }
}
