package com.dzgylxt.vo.catalog;

import lombok.Data;

import java.io.Serializable;

/**
 * 采购最低价同步标准价开关状态。
 */
@Data
public class LowestOrderSyncVO implements Serializable {

    /** 开关是否开启 */
    private boolean enabled;
    /** 上次开启时回写标准价的 SPU 条数 */
    private int lastUpdatedCount;

    public LowestOrderSyncVO() {
    }

    public LowestOrderSyncVO(boolean enabled, int lastUpdatedCount) {
        this.enabled = enabled;
        this.lastUpdatedCount = lastUpdatedCount;
    }
}
