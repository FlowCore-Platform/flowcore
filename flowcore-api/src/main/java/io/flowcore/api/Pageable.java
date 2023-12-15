package io.flowcore.api;

import java.util.Objects;

/**
 * Pagination request parameters — replaces Spring Data's {@code Pageable}
 * so the API module stays dependency-free.
 *
 * @param pageNumber zero-based page index
 * @param pageSize   maximum number of elements per page
 */
public record Pageable(int pageNumber, int pageSize) {

    /**
     * Creates a new {@code Pageable} with validation.
     *
     * @param pageNumber zero-based page index (must be &ge; 0)
     * @param pageSize   elements per page (must be &gt; 0)
     * @return the pageable
     * @throws IllegalArgumentException if arguments are out of range
     */
    public Pageable {
        if (pageNumber < 0) {
            throw new IllegalArgumentException("pageNumber must be >= 0, got: " + pageNumber);
        }
        if (pageSize <= 0) {
            throw new IllegalArgumentException("pageSize must be > 0, got: " + pageSize);
        }
    }

    /**
     * Convenience factory.
     *
     * @param pageNumber zero-based page index
     * @param pageSize   elements per page
     * @return a new {@code Pageable}
     */
    public static Pageable of(int pageNumber, int pageSize) {
        return new Pageable(pageNumber, pageSize);
    }

    /** @return zero-based offset into the overall result set */
    public long offset() {
        return (long) pageNumber * pageSize;
    }
}
