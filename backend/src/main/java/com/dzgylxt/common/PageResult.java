package com.dzgylxt.common;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 分页响应数据载体。
 */
@Data
public class PageResult<T> implements Serializable {

    /** 当前页数据 */
    private List<T> records;
    /** 总数 */
    private long total;
    /** 当前页码（从 1 开始） */
    private long current;
    /** 每页大小 */
    private long size;
    /** 总页数 */
    private long pages;
    /** 关联明细总数（如产品库列表的 SKU 规格项总数），未提供时为 0 */
    private long totalSkuCount;

    public static <T> PageResult<T> of(List<T> records, long total, long current, long size) {
        return of(records, total, current, size, 0L);
    }

    public static <T> PageResult<T> of(List<T> records, long total, long current, long size, long totalSkuCount) {
        PageResult<T> r = new PageResult<>();
        r.setRecords(records);
        r.setTotal(total);
        r.setCurrent(current);
        r.setSize(size);
        r.setPages(size == 0 ? 0 : (total + size - 1) / size);
        r.setTotalSkuCount(totalSkuCount);
        return r;
    }
}
