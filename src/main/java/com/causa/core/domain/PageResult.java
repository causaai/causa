package com.causa.core.domain;

import java.util.List;

/**
 * Generic paginated result — single wrapper used at every layer.
 *
 * <p>Returned by repository {@code search()} methods, passed through the service layer,
 * and serialised directly to JSON by the controllers.
 *
 * <p>The {@link #totalPages()} field is derived from {@code total} and {@code pageSize}
 * so callers never have to compute it themselves.
 *
 * @param <T>        the item type (domain object at the repository/service boundary;
 *                   response DTO at the controller boundary)
 * @param items      the current page of items (never {@code null}; may be empty)
 * @param page       the 1-based page index that was requested
 * @param pageSize   the maximum number of items per page
 * @param total      total number of matching records across all pages
 * @param totalPages total number of pages ({@code ceil(total / pageSize)})
 *
 * @since 0.0.2
 */
public record PageResult<T>(
        List<T> items,
        int page,
        int pageSize,
        long total,
        int totalPages
) {

    /**
     * Builds a {@link PageResult} from a pre-fetched item list, a total count,
     * and the originating {@link PageRequest}.
     *
     * @param <T>   the item type
     * @param items current-page items
     * @param total total matching record count
     * @param req   the page request that produced this result
     * @return a new {@link PageResult}
     */
    public static <T> PageResult<T> of(List<T> items, long total, PageRequest req) {
        int totalPages = req.size() == 0 ? 0 : (int) Math.ceil((double) total / req.size());
        return new PageResult<>(items, req.page(), req.size(), total, totalPages);
    }

    /**
     * Convenience factory for constructing a response from a pre-sliced list and a
     * known total — useful when the caller already has the items and count.
     *
     * @param <T>      item type
     * @param items    the sliced list for the current page
     * @param page     current page (1-based)
     * @param pageSize items per page
     * @param total    total item count across all pages
     * @return a new {@link PageResult}
     */
    public static <T> PageResult<T> of(List<T> items, int page, int pageSize, long total) {
        int totalPages = pageSize == 0 ? 0 : (int) Math.ceil((double) total / pageSize);
        return new PageResult<>(items, page, pageSize, total, totalPages);
    }

    /**
     * Returns {@code true} if the current page contains no items.
     *
     * @return {@code true} when {@code items} is empty
     */
    public boolean isEmpty() {
        return items.isEmpty();
    }
}
