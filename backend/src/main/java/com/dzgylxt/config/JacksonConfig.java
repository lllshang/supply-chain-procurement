package com.dzgylxt.config;

import com.baomidou.mybatisplus.annotation.IEnum;
import com.dzgylxt.common.BizException;
import com.dzgylxt.common.ResultCode;
import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.DeserializationConfig;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.deser.BeanDeserializerModifier;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.util.Arrays;

/**
 * Jackson 全局序列化/反序列化定制。
 *
 * <p><b>Long → String（QA #28）</b>：雪花 ID 为 19 位（&gt; 2^53），JavaScript
 * {@code JSON.parse} 会将其舍入（如 2102245025428721667 → 2102245025428721700），
 * 导致前端回传的 ID 与库中不一致。将 {@code Long / long} 统一序列化为字符串后，
 * 前端全程按字符串持有与回传，Jackson 反序列化字符串 → Long 无损。</p>
 *
 * <p><b>枚举 name 契约（QA #33 架构裁决 PR-1）</b>：API 层枚举统一为
 * 「字符串名字（name）」——<b>"两收一吐"</b>：</p>
 * <ul>
 *   <li><b>吐</b>：所有 {@code IEnum<Integer>} 枚举响应序列化 name
 *       （Jackson 默认行为，无 @JsonValue 即如此）。</li>
 *   <li><b>收</b>：注册全局 {@link IEnum} 反序列化器——先按 {@code name()}
 *       解析，失败再按 {@code getValue()} 数值兜底，旧格式数值请求天然兼容过渡。</li>
 * </ul>
 *
 * <p>DB 数值存储不动（MyBatis TypeHandler 保留）；RedisConfig 使用独立
 * ObjectMapper、EasyExcel 不涉及枚举，均不受影响。</p>
 */
@Configuration
public class JacksonConfig {

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer longToStringSerializerCustomizer() {
        return builder -> builder
                .serializerByType(Long.class, ToStringSerializer.instance)
                .serializerByType(Long.TYPE, ToStringSerializer.instance)
                .postConfigurer(objectMapper -> {
                    SimpleModule enumModule = new SimpleModule();
                    enumModule.setDeserializerModifier(new BeanDeserializerModifier() {
                        @Override
                        @SuppressWarnings({"unchecked", "rawtypes"})
                        public JsonDeserializer<?> modifyEnumDeserializer(DeserializationConfig config,
                                                                          com.fasterxml.jackson.databind.JavaType type,
                                                                          BeanDescription beanDesc,
                                                                          JsonDeserializer<?> deserializer) {
                            Class<?> enumClass = type.getRawClass();
                            if (IEnum.class.isAssignableFrom(enumClass)) {
                                return new IEnumNameDeserializer(enumClass);
                            }
                            return deserializer;
                        }
                    });
                    objectMapper.registerModule(enumModule);
                });
    }

    /**
     * IEnum 枚举全局反序列化器：先按 name 解析（统一契约），失败按 getValue()
     * 数值兜底（过渡期旧格式兼容）；两者皆失败抛 4000 参数错误。
     */
    static final class IEnumNameDeserializer extends StdDeserializer<Object> {

        private final Object[] constants;

        @SuppressWarnings({"unchecked", "rawtypes"})
        IEnumNameDeserializer(Class<?> enumClass) {
            super(enumClass);
            this.constants = enumClass.getEnumConstants();
        }

        @Override
        @SuppressWarnings({"unchecked", "rawtypes"})
        public Object deserialize(com.fasterxml.jackson.core.JsonParser parser,
                                  com.fasterxml.jackson.databind.DeserializationContext ctxt)
                throws IOException {
            String text = parser.getText() == null ? "" : parser.getText().trim();
            if (text.isEmpty()) {
                return null;
            }
            // ① name 优先（统一契约）
            for (Object constant : constants) {
                if (((Enum) constant).name().equalsIgnoreCase(text)) {
                    return constant;
                }
            }
            // ② getValue 数值兜底（旧格式兼容）
            for (Object constant : constants) {
                if (String.valueOf(((IEnum) constant).getValue()).equals(text)) {
                    return constant;
                }
            }
            throw new BizException(ResultCode.PARAM_ERROR,
                    "无效的枚举值：" + text + "（可选：" + names() + "）");
        }

        private String names() {
            return String.join("/", Arrays.stream(constants)
                    .map(c -> ((Enum<?>) c).name())
                    .toArray(String[]::new));
        }
    }
}
