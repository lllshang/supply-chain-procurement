package com.dzgylxt.common;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * Redis 分布式锁（下单三重校验的双保险层，设计 §4.2）。
 *
 * <p>行锁为主（FOR UPDATE），Redis 锁为多实例防穿透：SET NX PX + Lua 原子释放。
 * 获取失败快速失败（返回 false），由调用方转为「订单提交繁忙」业务异常。</p>
 */
@Component
public class RedisLockUtil {

    private static final Duration DEFAULT_TTL = Duration.ofSeconds(30);
    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then "
                    + "return redis.call('del', KEYS[1]) else return 0 end", Long.class);

    private final StringRedisTemplate redisTemplate;

    public RedisLockUtil(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /** 尝试加锁（阻塞最多 waitMillis），成功返回锁令牌、失败返回 null。 */
    public String tryLock(String key, long waitMillis) {
        String fullKey = "app:lock:" + key;
        String token = UUID.randomUUID().toString();
        long deadline = System.currentTimeMillis() + waitMillis;
        do {
            Boolean ok = redisTemplate.opsForValue()
                    .setIfAbsent(fullKey, token, DEFAULT_TTL);
            if (Boolean.TRUE.equals(ok)) {
                return token;
            }
            try {
                Thread.sleep(20);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            }
        } while (System.currentTimeMillis() < deadline);
        return null;
    }

    /** 释放锁（Lua 原子比较令牌，防止误删他人锁）。 */
    public void unlock(String key, String token) {
        if (key == null || token == null) {
            return;
        }
        redisTemplate.execute(UNLOCK_SCRIPT, List.of("app:lock:" + key), token);
    }
}
