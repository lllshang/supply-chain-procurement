package com.dzgylxt.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * JWT 工具：签发与解析 Access Token（含 uid / deptId / roles / perms）。
 */
@Component
public class JwtUtil {

    @Value("${app.jwt.secret}")
    private String secret;

    @Value("${app.jwt.issuer:dzgylxt}")
    private String issuer;

    private SecretKey key() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public String generateToken(LoginUser user, long validitySeconds) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .subject(user.getUsername())
                .issuer(issuer)
                .issuedAt(new Date(now))
                .expiration(new Date(now + validitySeconds * 1000))
                .claim("uid", user.getId())
                .claim("deptId", user.getMainDeptId())
                .claim("roles", String.join(",", user.getRoles()))
                .claim("perms", String.join(",", user.getPerms()))
                .signWith(key())
                .compact();
    }

    public LoginUser parseToken(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(key())
                .build()
                .parseSignedClaims(token)
                .getPayload();
        LoginUser user = new LoginUser();
        user.setUsername(claims.getSubject());
        user.setId(claims.get("uid", Long.class));
        user.setMainDeptId(claims.get("deptId", Long.class));
        user.setRoles(split(claims.get("roles", String.class)));
        user.setPerms(split(claims.get("perms", String.class)));
        user.setEnabled(true);
        return user;
    }

    public long getExpiration(String token) {
        Date exp = Jwts.parser().verifyWith(key()).build()
                .parseSignedClaims(token).getPayload().getExpiration();
        return (exp.getTime() - System.currentTimeMillis()) / 1000;
    }

    private List<String> split(String value) {
        if (value == null || value.isEmpty()) {
            return new ArrayList<>();
        }
        List<String> list = new ArrayList<>();
        for (String s : value.split(",")) {
            if (!s.isBlank()) {
                list.add(s.trim());
            }
        }
        return list;
    }
}
