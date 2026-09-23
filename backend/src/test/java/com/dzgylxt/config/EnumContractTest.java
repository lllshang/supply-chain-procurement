package com.dzgylxt.config;

import com.dzgylxt.enums.CoopStatus;
import com.dzgylxt.enums.ProductStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import com.fasterxml.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * #33 枚举 name 契约（架构裁决 PR-1）："两收一吐"。
 *
 * <ul>
 *   <li>吐：IEnum 枚举序列化为 name（CoopStatus.NORMAL → "NORMAL"）；</li>
 *   <li>收：name 优先解析，getValue() 数值兜底（旧格式兼容），非法值 4000。</li>
 * </ul>
 */
class EnumContractTest {

    private ObjectMapper mapper;

    /** DTO：承载被测字段（实体受 MyBatis 注解影响，用纯 DTO 隔离）。 */
    static class Dto {
        public CoopStatus coopStatus;
        public ProductStatus status;
        public Long id;
    }

    @BeforeEach
    void setUp() {
        Jackson2ObjectMapperBuilder builder = Jackson2ObjectMapperBuilder.json();
        new JacksonConfig().longToStringSerializerCustomizer().customize(builder);
        mapper = builder.build();
    }

    /** ① 吐：枚举序列化为 name（而非 getValue 数值）。 */
    @Test
    void serialize_enumAsName() throws Exception {
        Dto dto = new Dto();
        dto.coopStatus = CoopStatus.NORMAL;
        dto.status = ProductStatus.DISABLED;
        dto.id = 2102245025428721667L;
        String json = mapper.writeValueAsString(dto);
        org.junit.jupiter.api.Assertions.assertTrue(json.contains("\"coopStatus\":\"NORMAL\""), json);
        org.junit.jupiter.api.Assertions.assertTrue(json.contains("\"status\":\"DISABLED\""), json);
        // Long 字符串化不回退（#28）
        org.junit.jupiter.api.Assertions.assertTrue(json.contains("\"id\":\"2102245025428721667\""), json);
    }

    /** ② 收：name 解析（统一契约）。 */
    @Test
    void deserialize_name() throws Exception {
        Dto dto = mapper.readValue("{\"coopStatus\":\"FROZEN\",\"status\":\"NORMAL\"}", Dto.class);
        assertEquals(CoopStatus.FROZEN, dto.coopStatus);
        assertEquals(ProductStatus.NORMAL, dto.status);
    }

    /** ③ 收：数值兜底（旧格式 / P1 存量请求兼容）。 */
    @Test
    void deserialize_numericFallback() throws Exception {
        Dto dto = mapper.readValue("{\"coopStatus\":1,\"status\":0}", Dto.class);
        assertEquals(CoopStatus.DISABLED, dto.coopStatus);
        assertEquals(ProductStatus.NORMAL, dto.status);
    }

    /** ④ 收：非法值显式失败（BizException → 4000），不静默置 null。 */
    @Test
    void deserialize_invalidValue_fails() {
        assertThrows(Exception.class, () ->
                mapper.readValue("{\"coopStatus\":\"NOT_A_VALUE\"}", Dto.class));
    }

    /** ⑤ 收：空字符串不误映射枚举。 */
    @Test
    void deserialize_blank_null() throws Exception {
        Dto dto = mapper.readValue("{\"coopStatus\":\"\"}", Dto.class);
        assertInstanceOf(Dto.class, dto);
        assertEquals(null, dto.coopStatus);
    }
}
