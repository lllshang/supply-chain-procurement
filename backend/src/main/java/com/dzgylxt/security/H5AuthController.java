package com.dzgylxt.security;

import com.dzgylxt.common.R;
import com.dzgylxt.common.ResultCode;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.TimeUnit;

/**
 * H5 认证接口（供应商 H5 同 Token 体系，路径前缀 /h5/api/v1/auth）。
 */
@RestController
@RequestMapping("/h5/api/v1/auth")
public class H5AuthController {

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
        LoginResponse response = new LoginResponse();
        response.setToken(token);
        response.setExpiresIn(accessValidity);
        response.setUser(toUserInfo(loginUser));
        return R.ok(response);
    }

    @PostMapping("/logout")
    public R<Void> logout(@RequestHeader(value = "Authorization", required = false) String authorization) {
        if (authorization != null && authorization.startsWith("Bearer ")) {
            redisTemplate.delete(userTokenPrefix + authorization.substring(7));
        }
        return R.ok();
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
