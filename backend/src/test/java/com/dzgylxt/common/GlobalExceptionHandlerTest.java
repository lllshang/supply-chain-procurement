package com.dzgylxt.common;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

import java.lang.reflect.Method;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 畸形请求异常映射回归测试（P3-9）。
 *
 * <p>缺陷：{@link MissingServletRequestPartException} / {@link HttpMessageNotReadableException} /
 * {@link MultipartException} 此前未在 {@link GlobalExceptionHandler} 中映射，
 * 会落到 {@code handleUnknown} 兜底返回 HTTP 500 / code 1000。本测试确保统一返回 400 / code 4000
 * 且仍为 {@code {code, message, data, traceId}} 统一响应体。</p>
 */
class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;

    private GlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
        mockMvc = MockMvcBuilders.standaloneSetup(new ProbeController())
                .setControllerAdvice(handler)
                .build();
    }

    /** 用于触发各类畸形请求的探针控制器。 */
    @RestController
    static class ProbeController {

        @PostMapping("/probe/upload")
        public R<String> upload(@RequestPart("file") MultipartFile file) {
            return R.ok("ok");
        }

        @PostMapping("/probe/json")
        public R<String> json(@RequestBody Map<String, Object> body) {
            return R.ok("ok");
        }
    }

    /** multipart 请求缺少 file 字段 → 400 / 4000（原为 500 / 1000）。 */
    @Test
    void missingPart_returns400WithCode4000() throws Exception {
        mockMvc.perform(multipart("/probe/upload"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ResultCode.PARAM_ERROR.getCode()))
                .andExpect(jsonPath("$.traceId").exists());
    }

    /** multipart 携带了其它字段名（非 file）同样应被识别为缺少部件。 */
    @Test
    void wrongPartName_returns400WithCode4000() throws Exception {
        MockMultipartFile wrong = new MockMultipartFile("other", "a.txt", "text/plain", "x".getBytes());

        mockMvc.perform(multipart("/probe/upload").file(wrong))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ResultCode.PARAM_ERROR.getCode()));
    }

    /** 合法 multipart 不应被误判（控制组）。 */
    @Test
    void validPart_returns200() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "a.txt", "text/plain", "x".getBytes());

        mockMvc.perform(multipart("/probe/upload").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ResultCode.SUCCESS.getCode()));
    }

    /** 畸形 JSON 请求体 → 400 / 4000。 */
    @Test
    void malformedJson_returns400WithCode4000() throws Exception {
        mockMvc.perform(post("/probe/json")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ResultCode.PARAM_ERROR.getCode()));
    }

    /** 合法 JSON 请求体不应被误判（控制组）。 */
    @Test
    void validJson_returns200() throws Exception {
        mockMvc.perform(post("/probe/json")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"abc\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ResultCode.SUCCESS.getCode()));
    }

    /** MultipartException 直接映射：4000 + 统一响应体含 traceId。 */
    @Test
    void multipartException_mapsToParamError() {
        R<Void> r = handler.handleBadRequest(new MultipartException("上传解析失败"));

        assertEquals(ResultCode.PARAM_ERROR.getCode(), r.getCode());
        assertNotNull(r.getTraceId(), "统一响应体应包含 traceId");
    }

    /** 契约校验：handler 标注 400，且 @ExceptionHandler 覆盖三种异常类型。 */
    @Test
    void handlerContract_expectsBadRequestAndThreeTypes() throws NoSuchMethodException {
        Method method = GlobalExceptionHandler.class.getMethod("handleBadRequest", Exception.class);

        ResponseStatus responseStatus = AnnotatedElementUtils.findMergedAnnotation(method, ResponseStatus.class);
        assertNotNull(responseStatus, "handleBadRequest 应标注 @ResponseStatus");
        assertEquals(HttpStatus.BAD_REQUEST, responseStatus.value());

        ExceptionHandler eh = AnnotatedElementUtils.findMergedAnnotation(method, ExceptionHandler.class);
        assertNotNull(eh);
        assertTrue(java.util.Arrays.asList(eh.value()).contains(MissingServletRequestPartException.class),
                "应映射 MissingServletRequestPartException");
        assertTrue(java.util.Arrays.asList(eh.value()).contains(HttpMessageNotReadableException.class),
                "应映射 HttpMessageNotReadableException");
        assertTrue(java.util.Arrays.asList(eh.value()).contains(MultipartException.class),
                "应映射 MultipartException");
    }
}
