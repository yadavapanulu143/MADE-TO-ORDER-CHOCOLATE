package com.choco.fulfilment.application.support;

import com.choco.core.page.PageResult;
import com.choco.core.port.PageQuery;
import java.util.List;

/** Shared paging helper for the in-memory repositories, matching the page-of-a-list semantics. */
final class InMemoryPage {

    private InMemoryPage() {
    }

    static <T> PageResult<T> of(List<T> all, PageQuery page) {
        PageQuery effective = page == null ? new PageQuery(0, 20, null, true) : page;
        int from = Math.min(effective.offset(), all.size());
        int to = Math.min(from + effective.size(), all.size());
        return PageResult.of(all.subList(from, to), effective.page(), effective.size(), all.size());
    }
}
