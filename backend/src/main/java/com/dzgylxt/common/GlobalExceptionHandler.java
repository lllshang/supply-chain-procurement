package com.dzgylxt.common;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.stream.Collectors;

/**
 * 全局异常处理器：统一将异常转换为 {@link R}。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BizException.class)
    public R<Void> handleBiz(BizException e) {
        log.warn("业务异常: {}", e.getMessage());
        return R.fail(e.getCode(), e.getMessage());
    }

    /**
     * 参数校验业务异常：HTTP 400 / code 4000（P3-20）。
     *
     * <p>{@link ParamException} 是 {@link BizException} 的子类，异常解析按最具体类型
     * 优先命中本处理器；通用 {@link #handleBiz} 行为不变（其余业务异常仍为 HTTP 200
     * + 业务码，如登录失败 2010）。响应体仍为统一 {@code {code, message, data, traceId}}。</p>
     */
    @ExceptionHandler(ParamException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public R<Void> handleParam(ParamException e) {
        log.warn("参数错误: {}", e.getMessage());
        return R.fail(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(AuthenticationException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public R<Void> handleAuth(AuthenticationException e) {
        return R.fail(ResultCode.UNAUTHORIZED.getCode(), ResultCode.UNAUTHORIZED.getMessage());
    }

    @ExceptionHandler(AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public R<Void> handleAccessDenied(AccessDeniedException e) {
        return R.fail(ResultCode.FORBIDDEN.getCode(), ResultCode.FORBIDDEN.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public R<Void> handleValid(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return R.fail(ResultCode.PARAM_ERROR.getCode(), msg);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public R<Void> handleMissingParam(MissingServletRequestParameterException e) {
        return R.fail(ResultCode.PARAM_ERROR.getCode(), "缺少必填参数: " + e.getParameterName());
    }

    /**
     * 畸形请求统一返回 400 / code 4000（P3-9）。
     *
     * <p>覆盖三类此前未映射、会落到 {@code handleUnknown} 兜底返回 500 / 1000 的异常：</p>
     * <ul>
     *   <li>{@link MissingServletRequestPartException}：multipart 请求缺少 {@code file} 等部件；</li>
     *   <li>{@link HttpMessageNotReadableException}：请求体不是合法 JSON；</li>
     *   <li>{@link MultipartException}：multipart 解析失败（含体积超限等子类）。</li>
     * </ul>
     *
     * <p>响应体仍为统一 {@code {code, message, data, traceId}}，code 采用参数校验错误码 4000。</p>
     */
    @ExceptionHandler({
            MissingServletRequestPartException.class,
            HttpMessageNotReadableException.class,
            MultipartException.class
    })
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public R<Void> handleBadRequest(Exception e) {
        log.warn("请求体非法: {}", e.getMessage());
        return R.fail(ResultCode.PARAM_ERROR.getCode(), badRequestMessage(e));
    }

    /** 依据异常类型给出可读的参数错误描述。 */
    private String badRequestMessage(Exception e) {
        if (e instanceof MissingServletRequestPartException) {
            return "缺少必填的请求部件: " + ((MissingServletRequestPartException) e).getRequestPartName();
        }
        if (e instanceof HttpMessageNotReadableException) {
            return "请求体格式非法，无法解析";
        }
        return "multipart 请求解析失败";
    }

    @ExceptionHandler(NoResourceFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public R<Void> handleNoResource(NoResourceFoundException e) {
        return R.fail(ResultCode.NOT_FOUND.getCode(), ResultCode.NOT_FOUND.getMessage());
    }

    @ExceptionHandler(NoHandlerFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public R<Void> handleNoHandler(NoHandlerFoundException e) {
        return R.fail(ResultCode.NOT_FOUND.getCode(), ResultCode.NOT_FOUND.getMessage());
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public R<Void> handleUnknown(Exception e, HttpServletRequest request) {
        log.error("系统异常 uri={}", request.getRequestURI(), e);
        return R.fail(ResultCode.SYSTEM_ERROR.getCode(), ResultCode.SYSTEM_ERROR.getMessage());
    }
}
