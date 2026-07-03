package com.github.chjiae.common.result;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * Paginated response wrapper.
 *
 * @param <T> the type of items in the page
 */
@Data
public class PageResult<T> implements Serializable {

    private List<T> list;
    private long total;
    private int page;
    private int size;

    private PageResult() {}

    public static <T> PageResult<T> of(List<T> list, long total, int page, int size) {
        PageResult<T> result = new PageResult<>();
        result.setList(list);
        result.setTotal(total);
        result.setPage(page);
        result.setSize(size);
        return result;
    }

    public static <T> PageResult<T> empty(int page, int size) {
        return of(List.of(), 0, page, size);
    }
}
