package com.choco.core.page;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.choco.core.port.PageQuery;
import java.util.List;
import org.junit.jupiter.api.Test;

class PageResultTest {

    @Test
    void computes_total_pages_and_next_flag() {
        PageResult<String> page = PageResult.of(List.of("a", "b"), 0, 2, 5);
        assertThat(page.totalPages()).isEqualTo(3);
        assertThat(page.hasNext()).isTrue();
        assertThat(PageResult.of(List.of("x"), 2, 2, 5).hasNext()).isFalse();
    }

    @Test
    void maps_content_preserving_metadata() {
        PageResult<String> page = PageResult.of(List.of("1", "2"), 1, 2, 10);
        PageResult<Integer> mapped = page.map(Integer::valueOf);
        assertThat(mapped.content()).containsExactly(1, 2);
        assertThat(mapped.totalElements()).isEqualTo(10);
        assertThat(mapped.page()).isEqualTo(1);
    }

    @Test
    void page_query_caps_size_and_rejects_negative_page() {
        assertThatThrownBy(() -> PageQuery.of(-1, 10, null, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PageQuery.of(0, PageQuery.MAX_PAGE_SIZE + 1, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(PageQuery.of(null, null, null, null).size()).isEqualTo(PageQuery.DEFAULT_PAGE_SIZE);
        assertThat(PageQuery.of(2, 10, null, null).offset()).isEqualTo(20);
    }
}
