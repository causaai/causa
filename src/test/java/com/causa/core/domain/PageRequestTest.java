package com.causa.core.domain;

import com.causa.common.constants.ApiConstants;
import com.causa.common.exceptions.InvalidPaginationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.*;

@DisplayName("PageRequest Tests")
class PageRequestTest {

    private static final int DEFAULT_SIZE =
            Integer.parseInt(ApiConstants.Paths.Pagination.DEFAULT_PAGE_SIZE);
    private static final int MAX_SIZE = ApiConstants.Paths.Pagination.MAX_PAGE_SIZE;

    @Nested @DisplayName("normalized() Page Size Tests")
    class PageSizeTests {
        @ParameterizedTest @ValueSource(ints = {0, -1, -100})
        void nonPositiveSizeFallsBackToDefault(int size) {
            assertThat(PageRequest.of(1, size).normalized().size()).isEqualTo(DEFAULT_SIZE);
        }
        @ParameterizedTest @ValueSource(ints = {1, 50, 100})
        void validSizeIsKept(int size) {
            assertThat(PageRequest.of(1, size).normalized().size()).isEqualTo(size);
        }
        @Test void sizeAboveMaxThrows() {
            assertThatThrownBy(() -> PageRequest.of(1, MAX_SIZE + 1).normalized())
                .isInstanceOf(InvalidPaginationException.class)
                .hasMessage("page_size must be between 1 and " + MAX_SIZE);
        }
    }

    @Nested @DisplayName("normalized() Page Index Tests")
    class PageIndexTests {
        @ParameterizedTest @ValueSource(ints = {0, -1, -100})
        void nonPositivePageFallsBackToFirstPage(int page) {
            assertThat(PageRequest.of(page, 10).normalized().page()).isEqualTo(1);
        }
        @Test void validPageIsKept() {
            assertThat(PageRequest.of(3, 10).normalized().page()).isEqualTo(3);
        }
    }
}
