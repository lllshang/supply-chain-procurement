package com.dzgylxt.config;

import com.baomidou.mybatisplus.annotation.IEnum;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.core.convert.converter.ConverterFactory;
import org.springframework.format.FormatterRegistry;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Arrays;

/**
 * Web MVC 枚举查询参数绑定（#33 枚举 name 契约 PR-2）。
 *
 * <p>Query 参数 / 表单绑定不走 Jackson（{@code JacksonConfig} 只管 JSON 体），
 * 此处注册全局 {@code String → IEnum 枚举} 的 ConverterFactory：
 * 先按 {@code name()} 解析（统一契约），失败按 {@code getValue()} 数值兜底
 * （P1 前端存量数值参数天然兼容）；两者皆失败抛 IllegalArgumentException（400）。
 * 非 IEnum 枚举回退 Spring 默认 {@code Enum.valueOf}。</p>
 */
@Configuration
public class EnumWebConfig implements WebMvcConfigurer {

    @Override
    public void addFormatters(@NonNull FormatterRegistry registry) {
        registry.addConverterFactory(new IEnumConverterFactory());
    }

    /** String → 枚举转换工厂：IEnum 枚举 name 优先、数值兜底。 */
    static final class IEnumConverterFactory implements ConverterFactory<String, Enum> {

        @Override
        @NonNull
        @SuppressWarnings({"unchecked", "rawtypes"})
        public <T extends Enum> Converter<String, T> getConverter(@NonNull Class<T> targetType) {
            return source -> {
                String text = source == null ? "" : source.trim();
                if (text.isEmpty()) {
                    return null;
                }
                if (IEnum.class.isAssignableFrom(targetType)) {
                    for (Object constant : targetType.getEnumConstants()) {
                        if (((Enum) constant).name().equalsIgnoreCase(text)) {
                            return (T) constant;
                        }
                    }
                    for (Object constant : targetType.getEnumConstants()) {
                        if (String.valueOf(((IEnum) constant).getValue()).equals(text)) {
                            return (T) constant;
                        }
                    }
                    throw new IllegalArgumentException(
                            "无效的枚举值：" + text + "（可选：" + names(targetType) + "）");
                }
                return (T) Enum.valueOf(targetType, text);
            };
        }

        private String names(Class<?> type) {
            return String.join("/", Arrays.stream(type.getEnumConstants())
                    .map(c -> ((Enum<?>) c).name())
                    .toArray(String[]::new));
        }
    }
}
