package com.dzgylxt.common;

/**
 * 参数校验业务异常（P3-20）。
 *
 * <p>与通用 {@link BizException} 的区别：由 {@link GlobalExceptionHandler}
 * 映射为 <b>HTTP 400 + code 4000</b>，而非 HTTP 200 + 业务码。用于「请求参数
 * 缺失 / 非法且不应落库」的场景（如创建用户缺少密码）。</p>
 */
public class ParamException extends BizException {

    public ParamException(String message) {
        super(ResultCode.PARAM_ERROR.getCode(), message);
    }
}
