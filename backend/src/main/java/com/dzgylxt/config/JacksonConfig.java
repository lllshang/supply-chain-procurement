package com.dzgylxt.config;

import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Jackson 全局序列化定制（QA #28）。
 *
 * <p>雪花 ID 为 19 位（&gt; 2^53），JavaScript {@code JSON.parse} 会将其舍入
 * （如 2102245025428721667 → 2102245025428721700），导致前端回传的 ID 与库中
 * 不一致——UI 写操作近乎全废。将 {@code Long / long} 统一序列化为字符串后，
 * 前端全程按字符串持有与回传，Jackson 反序列化字符串 → Long 无损。</p>
 *
 * <p>注意：请求侧无需定制——Jackson 默认支持字符串 → Long 的宽松反序列化；
 * 数值枚举（IEnum）仍按枚举名序列化，不受影响。</p>
 */
@Configuration
public class JacksonConfig {

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer longToStringSerializerCustomizer() {
        return builder -> builder
                .serializerByType(Long.class, ToStringSerializer.instance)
                .serializerByType(Long.TYPE, ToStringSerializer.instance);
    }
}
