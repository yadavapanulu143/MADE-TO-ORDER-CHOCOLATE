package com.choco.catalog.application.dto;

import java.util.UUID;

/**
 * An image as presented to a caller.
 *
 * <p>The {@code url} is a path the caller can fetch, derived from the storage key. The key itself is
 * not exposed, so a client cannot construct a request for an arbitrary stored object.
 */
public record ImageView(
        UUID id,
        String url,
        String altText,
        int displayOrder,
        boolean primary) {
}
