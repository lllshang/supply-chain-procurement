package com.dzgylxt.common;

import lombok.Getter;

/**
 * 业务异常。携带响应码与消息，由 {@link GlobalExceptionHandler} 统一转换为 {@link R}。
 */
@Getter
public class BizException extends RuntimeException {

    private final int code;

    public BizException(IResultCode resultCode) {
        super(resultCode.getMessage());
        this.code = resultCode.getCode();
    }

    public BizException(IResultCode resultCode, String detail) {
        super(resultCode.getMessage() + "：" + detail);
        this.code = resultCode.getCode();
    }

    public BizException(int code, String message) {
        super(message);
        this.code = code;
    }
}
