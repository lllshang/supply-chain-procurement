package com.dzgylxt.common;

import lombok.Getter;

/**
 * 统一响应码枚举实现。
 */
@Getter
public enum ResultCode implements IResultCode {

    SUCCESS(0, "成功"),

    // 1xxx 系统
    SYSTEM_ERROR(1000, "系统异常，请稍后重试"),
    GATEWAY_TIMEOUT(1001, "网关超时"),
    SERVICE_UNAVAILABLE(1002, "服务暂时不可用"),

    // 2xxx 身份 / 权限
    UNAUTHORIZED(2001, "未登录或登录已过期"),
    TOKEN_INVALID(2002, "令牌无效或已被踢出"),
    TOKEN_EXPIRED(2003, "令牌已过期"),
    FORBIDDEN(2004, "无访问权限"),
    LOGIN_FAILED(2010, "用户名或密码错误"),
    CAPTCHA_ERROR(2011, "验证码错误"),

    // 3xxx 业务
    BIZ_ERROR(3000, "业务异常"),
    DATA_NOT_FOUND(3001, "数据不存在"),
    DATA_CONFLICT(3002, "数据冲突，请刷新后重试"),
    STATUS_INVALID(3003, "当前状态不允许该操作"),
    BUDGET_EXCEED(3010, "超出可用预算"),

    // 4xxx 参数
    PARAM_ERROR(4000, "参数校验失败"),
    METHOD_NOT_ALLOWED(4005, "请求方法不被允许"),
    NOT_FOUND(4040, "请求的资源不存在");

    private final int code;
    private final String message;

    ResultCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}
