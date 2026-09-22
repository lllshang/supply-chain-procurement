package com.dzgylxt.mapper.integration;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dzgylxt.entity.integration.OutboxEvent;
import org.apache.ibatis.annotations.Mapper;

/** Outbox 事件 Mapper。 */
@Mapper
public interface OutboxEventMapper extends BaseMapper<OutboxEvent> {
}
