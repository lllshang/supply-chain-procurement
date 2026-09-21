package com.dzgylxt.common;

/**
 * 统一响应码契约。
 *
 * <p>错误码分段：0=成功；1xxx=系统；2xxx=身份/权限；3xxx=业务；4xxx=参数校验。</p>
 */
public interface IResultCode {

    int getCode();

    String getMessage();
}
