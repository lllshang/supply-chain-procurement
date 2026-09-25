package com.dzgylxt.integration;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.dzgylxt.entity.integration.OutboxEvent;
import com.dzgylxt.enums.OutboxStatus;
import com.dzgylxt.mapper.integration.OutboxEventMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Outbox 消费者：定时扫描 {@code status=PENDING} 事件 → 调 {@link Adapter} 投递 → 标记 SENT/RETRY。
 * 一期 Adapter 为占位（{@link NoopAdapter}），仅完成落库状态机，不阻断业务。
 */
@Slf4j
@Component
public class IntegrationWorker {

    /** Outbox 状态：0=PENDING 1=SENT 2=RETRY 3=FAILED */
    private static final int MAX_RETRY = 5;

    private final OutboxEventMapper outboxEventMapper;
    private final Adapter adapter;

    public IntegrationWorker(OutboxEventMapper outboxEventMapper, Adapter adapter) {
        this.outboxEventMapper = outboxEventMapper;
        this.adapter = adapter;
    }

    @Scheduled(fixedDelay = 5000)
    @Transactional
    public void poll() {
        List<OutboxEvent> pending = outboxEventMapper.selectList(
                Wrappers.<OutboxEvent>lambdaQuery()
                        .eq(OutboxEvent::getStatus, OutboxStatus.PENDING));
        for (OutboxEvent event : pending) {
            try {
                boolean ok = adapter.deliver(event);
                if (ok) {
                    event.setStatus(OutboxStatus.SENT);
                } else {
                    event.setStatus(OutboxStatus.RETRY);
                    event.setRetry(nextRetry(event));
                }
                outboxEventMapper.updateById(event);
            } catch (Exception e) {
                log.warn("outbox deliver failed eventId={}", event.getId(), e);
                event.setStatus(OutboxStatus.RETRY);
                event.setRetry(nextRetry(event));
                outboxEventMapper.updateById(event);
            }
        }
    }

    private int nextRetry(OutboxEvent event) {
        int next = (event.getRetry() == null ? 0 : event.getRetry()) + 1;
        if (next > MAX_RETRY) {
            event.setStatus(OutboxStatus.FAILED);
        }
        return next;
    }
}
