package com.choco.core.port;

/**
 * Normalised pagination input. Page size is capped so a caller cannot ask for the entire customer
 * table in one request, which would be both a performance and a data-protection problem.
 */
public record PageQuery(int page, int size, String sort, boolean ascending) {

    public static final int MAX_PAGE_SIZE = 100;
    public static final int DEFAULT_PAGE_SIZE = 20;

    public PageQuery {
        if (page < 0) {
            throw new IllegalArgumentException("Page index must not be negative");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("Page size must be between 1 and " + MAX_PAGE_SIZE);
        }
    }

    public static PageQuery of(Integer page, Integer size, String sort, Boolean ascending) {
        return new PageQuery(
                page == null ? 0 : page,
                size == null ? DEFAULT_PAGE_SIZE : size,
                sort,
                ascending == null || ascending);
    }

    public int offset() {
        return page * size;
    }
}
