package com.dzgylxt.security;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 登录用户主体（作为 Spring Security Authentication 的 principal）。
 */
@Data
public class LoginUser implements UserDetails {

    private Long id;
    private String username;
    /** BCrypt 哈希（仅用于 UserDetails，不对外暴露） */
    private String password;
    private Long mainDeptId;
    private List<String> roles = new ArrayList<>();
    private List<String> perms = new ArrayList<>();
    private boolean enabled = true;

    @Override
    @JsonIgnore
    public Collection<? extends GrantedAuthority> getAuthorities() {
        Set<String> auths = roles.stream().map(r -> "ROLE_" + r).collect(Collectors.toSet());
        auths.addAll(perms);
        return auths.stream().map(SimpleGrantedAuthority::new).collect(Collectors.toList());
    }

    @Override
    @JsonIgnore
    public String getPassword() {
        return password;
    }

    @Override
    @JsonIgnore
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    @JsonIgnore
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    @JsonIgnore
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }
}
