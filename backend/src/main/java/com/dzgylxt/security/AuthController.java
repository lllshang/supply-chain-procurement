package com.dzgylxt.security;

import com.dzgylxt.common.BizException;
import com.dzgylxt.common.R;
import com.dzgylxt.common.ResultCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.TimeUnit;

/**
 * 认证接口：登录 / 当前用户 / 登出（后台与 H5 共用 Token 体系）。
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    @Autowired
    private AuthProvider authProvider;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Value("${app.jwt.access-token-validity-in-seconds:7200}")
    private long accessValidity;

    @Value("${app.redis.user-token-prefix:USER:TOKEN:}")
    private String userTokenPrefix;

    @PostMapping("/login")
    public R<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        LoginUser loginUser = authProvider.authenticate(request.getUsername(), request.getPassword());
        String token = jwtUtil.generateToken(loginUser, accessValidity);
        redisTemplate.opsForValue().set(userTokenPrefix + token, loginUser, accessValidity, TimeUnit.SECONDS);
        return R.ok(toResponse(loginUser, token));
    }

    /** 当前登录用户信息 */
    @GetMapping("/me")
    public R<LoginResponse.UserInfo> me() {
        LoginUser loginUser = UserContext.get();
        if (loginUser == null) {
            throw new BizException(ResultCode.UNAUTHORIZED);
        }
        return R.ok(toUserInfo(loginUser));
    }

    @PostMapping("/logout")
    public R<Void> logout(@RequestHeader(value = "Authorization", required = false) String authorization) {
        if (authorization != null && authorization.startsWith("Bearer ")) {
            redisTemplate.delete(userTokenPrefix + authorization.substring(7));
        }
        return R.ok();
    }

    private LoginResponse toResponse(LoginUser loginUser, String token) {
        LoginResponse response = new LoginResponse();
        response.setToken(token);
        response.setExpiresIn(accessValidity);
        response.setUser(toUserInfo(loginUser));
        return response;
    }

    private LoginResponse.UserInfo toUserInfo(LoginUser loginUser) {
        LoginResponse.UserInfo info = new LoginResponse.UserInfo();
        info.setId(loginUser.getId());
        info.setUsername(loginUser.getUsername());
        info.setDeptId(loginUser.getMainDeptId());
        info.setRoles(loginUser.getRoles());
        info.setPerms(loginUser.getPerms());
        return info;
    }
}
