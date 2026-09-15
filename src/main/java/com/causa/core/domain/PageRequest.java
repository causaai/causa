package com.causa.core.domain;

/**
 * Pagination request value object.
 *
 * <p>Pure value carrier — no defaults or caps live here. The default page size
 * and max page size cap are defined as constants in
 * {@link com.causa.common.constants.ApiConstants.Paths.Pagination} and resolved
 * by the service layer before constructing this record.
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
}
