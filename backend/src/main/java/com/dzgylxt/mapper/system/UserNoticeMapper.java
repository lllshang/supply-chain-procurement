package com.dzgylxt.mapper.system;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dzgylxt.entity.system.UserNotice;
import org.apache.ibatis.annotations.Mapper;

/** 站内通知 Mapper（P4 §3 通知）。 */
@Mapper
public interface UserNoticeMapper extends BaseMapper<UserNotice> {
}
