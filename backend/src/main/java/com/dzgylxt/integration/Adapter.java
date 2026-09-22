package com.dzgylxt.integration;

import com.dzgylxt.entity.integration.OutboxEvent;

/**
 * 跨系统适配层接口。一期用 {@link NoopAdapter} 占位；后续按零售/餐饮等对接系统实现具体 Adapter。
 */
public interface Adapter {

    /**
     * 投递事件到外部系统。返回 true 表示成功。
     */
    boolean deliver(OutboxEvent event);
}
