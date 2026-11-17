package com.choco.catalog.domain.product;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * URL-safe identifier for a product, derived from its name.
 *
 * <p>Customers link to and share product pages, so the address has to be stable and readable. A slug
 * is derived once, at creation, and never regenerated when the name changes: rewriting it would break
 * every existing link, and a shared link that silently starts returning 404 is worse than an address
 * that no longer matches the current title exactly.
 */
public record Slug(String value) {

    private static final Pattern VALID = Pattern.compile("^[a-z0-9]+(?:-[a-z0-9]+)*$");
    private static final int MAX_LENGTH = 80;

    public Slug {
        if (value == null || !VALID.matcher(value).matches()) {
            throw new IllegalArgumentException("Invalid slug: " + value);
        }
        if (value.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("Slug must be at most " + MAX_LENGTH + " characters");
        }
    }

    public static Slug of(String value) {
        return new Slug(value);
    }

    /**
     * Builds a slug from a product name.
     *
     * <p>Accented characters are decomposed and stripped so "Café Noir" becomes {@code cafe-noir}
     * rather than losing the word. Anything that is not a letter or digit becomes a single hyphen,
     * and the result is trimmed of leading and trailing hyphens.
     */
    public static Slug fromName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("A product name is required to build a slug");
        }
        String decomposed = Normalizer.normalize(name, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");
        String slug = decomposed.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-+)|(-+$)", "");
        if (slug.isEmpty()) {
            throw new IllegalArgumentException("A product name must contain at least one letter or digit");
        }
        if (slug.length() > MAX_LENGTH) {
            slug = slug.substring(0, MAX_LENGTH).replaceAll("-+$", "");
        }
        return new Slug(slug);
    }

    @Override
    public String toString() {
        return value;
    }
}
