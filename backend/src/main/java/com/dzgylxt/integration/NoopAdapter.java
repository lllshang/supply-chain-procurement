package com.dzgylxt.integration;

import com.dzgylxt.entity.integration.OutboxEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

/**
 * 一期占位适配层：不连外部系统，直接返回成功，不阻断采购闭环。
 * 后续按对接系统实现具体 {@link Adapter} 替换本类即可。
 */
@Slf4j
@Service
@Primary
public class NoopAdapter implements Adapter {

    @Override
    public boolean deliver(OutboxEvent event) {
        log.info("[Adapter][占位] 跳过外部投递 eventId={} type={}", event.getId(), event.getType());
        return true;
    }
}
