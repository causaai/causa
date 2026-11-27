package com.causa.core.domain;

import com.causa.common.constants.ApiConstants;
import com.causa.common.exceptions.InvalidPaginationException;

/**
 * Pagination request value object.
 *
 * <p>Carries the raw 1-based page index and page size from the API. Call
 * {@link #normalized()} to apply the default page size, the max page size cap,
 * and the minimum page index defined in
 * {@link com.causa.common.constants.ApiConstants.Paths.Pagination}.
 *
 * <p>Page index is <strong>1-based</strong> externally (matching the API contract).
 * Use {@link #panachePage()} to obtain the 0-based index required by Panache.
 *
 * @since 0.0.2
 */
public record PageRequest(
        int page,
        int size
) {

    /**
     * Creates a {@link PageRequest}.
     *
     * @param page 1-based page index (must be ≥ 1)
     * @param size items per page
     * @return a new {@link PageRequest}
     */
    public static PageRequest of(int page, int size) {
        return new PageRequest(page, size);
    }

    /**
     * Returns the 0-based page index required by Panache's {@code Page.of(index, size)}.
     *
     * @return {@code page - 1}
     */
    public int panachePage() {
        return page - 1;
    }

    /**
     * Returns a copy of this request with pagination rules applied:
     * <ul>
     *   <li>{@code size <= 0} falls back to the default page size</li>
     *   <li>{@code size} above the max page size is rejected</li>
     *   <li>{@code page <= 0} falls back to the first page</li>
     * </ul>
     *
     * @return a validated {@link PageRequest}
     * @throws InvalidPaginationException if {@code size} exceeds the max page size
     */
    public PageRequest normalized() {
        int resolvedSize = size <= 0
                ? Integer.parseInt(ApiConstants.Paths.Pagination.DEFAULT_PAGE_SIZE)
                : size;
        if (resolvedSize > ApiConstants.Paths.Pagination.MAX_PAGE_SIZE) {
            throw new InvalidPaginationException(
                    "page_size must be between 1 and " + ApiConstants.Paths.Pagination.MAX_PAGE_SIZE);
        }
        int resolvedPage = page <= 0 ? 1 : page;
        return new PageRequest(resolvedPage, resolvedSize);
    }
}
