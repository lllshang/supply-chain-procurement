package com.dzgylxt.common;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * 业务单据编号生成器（设计 §2：单据编号统一生成，禁止业务散拼）。
 *
 * <p>规则：{@code {prefix}-{yyyy}{MM}-{seq6}}，序号由 <b>Redis INCR</b> 生成、
 * <b>按月重置</b>（key 携带年月，过期时间 40 天 > 31 天月长度，保证跨月自然从 1 起）。
 * 既有 {@link IdGenerator}（Snowflake）保持不变，仅用于非编号场景。</p>
 *
 * <p>P2 前缀约定（对齐设计 §1.2.7 / §1.3.4 / §1.3.5 / §1.3.9）：
 * 采购申请 CG / 询价 XJ / 定标 DB / 合同 HT / 订单 DD / 履约调整 LY；
 * 到货单 DH-{order_no}-{seq2}、报价批次 BJ-{inquiry_no}-{seq2} 由各 Service 用
 * {@link #nextSeq} 按“父单号 + 序号”规则组装。</p>
 */
@Component
public class BusinessNoGenerator {

    private static final DateTimeFormatter MONTH_FMT = DateTimeFormatter.ofPattern("yyyyMM");
    /** 序号 key 过期时间：40 天 > 任何月长度，跨月后旧 key 自动清理、新月从 1 起。 */
    private static final Duration SEQ_TTL = Duration.ofDays(40);

    private final StringRedisTemplate redisTemplate;

    @Autowired
    public BusinessNoGenerator(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 生成按月重置的业务单号：{@code {prefix}-{yyyy}{MM}-{seq6}}。
     *
     * @param prefix 单据前缀（如 CG / XJ / DB / HT / DD / LY）
     * @return 形如 CG-202609-000001 的单据编号
     */
    public String nextNo(String prefix) {
        LocalDate today = LocalDate.now();
        String month = today.format(MONTH_FMT);
        long seq = nextSeq("NO:" + prefix + ":" + month);
        return String.format("%s-%s-%06d", prefix, month, seq);
    }

    /**
     * 生成父单号下的序号（如到货单 DH-{order_no}-{seq2}、报价批次 BJ-{inquiry_no}-{seq2}）。
     *
     * @param parentNo 父单号（order_no / inquiry_no）
     * @param width    序号位数（2 或 3）
     */
    public String nextSubSeq(String parentNo, int width) {
        long seq = nextSeq("SUB:" + parentNo);
        return String.format("%0" + width + "d", seq);
    }

    /** Redis INCR 取号；Redis 异常时降级为毫秒时间戳序号（可用性优先，单号仍全局唯一）。 */
    private long nextSeq(String key) {
        String fullKey = "app:seq:" + key;
        try {
            Long seq = redisTemplate.opsForValue().increment(fullKey);
            if (seq != null) {
                if (seq == 1L) {
                    // 首次取号时设置过期，驱动按月/按父单重置
                    redisTemplate.expire(fullKey, SEQ_TTL);
                }
                return seq;
            }
        } catch (RuntimeException ignored) {
            // 降级路径：见方法注释
        }
        return System.currentTimeMillis() % 1_000_000_000L;
    }
}
