package com.dzgylxt.config;

import com.dzgylxt.common.R;
import com.dzgylxt.common.ResultCode;
import com.dzgylxt.security.JwtAuthFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/**
 * Spring Security 配置：JWT 无状态 + CORS + 放行白名单 + 方法级鉴权。
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Autowired
    private JwtAuthFilter jwtAuthFilter;

    @Value("${app.jwt.permit-urls:}")
    private String permitUrls;

    @Value("${app.cors.allowed-origins:}")
    private String corsAllowedOrigins;

    @Value("${app.cors.allowed-methods:*}")
    private String corsAllowedMethods;

    @Value("${app.cors.allowed-headers:*}")
    private String corsAllowedHeaders;

    @Value("${app.cors.allow-credentials:true}")
    private boolean corsAllowCredentials;

    @Value("${app.cors.max-age:3600}")
    private long corsMaxAge;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        List<String> urls = Arrays.stream(permitUrls.split(","))
                .map(String::trim).filter(s -> !s.isEmpty()).toList();
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> {
                    urls.forEach(url -> auth.requestMatchers(url).permitAll());
                    auth.anyRequest().authenticated();
                })
                // 未认证 → 统一 401；已认证但无权限 → 统一 403（见 AuthzService + @PreAuthorize）
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, authEx) ->
                                writeUnified(response, HttpServletResponse.SC_UNAUTHORIZED,
                                        ResultCode.UNAUTHORIZED.getCode(), ResultCode.UNAUTHORIZED.getMessage()))
                        .accessDeniedHandler((request, response, deniedEx) ->
                                writeUnified(response, HttpServletResponse.SC_FORBIDDEN,
                                        ResultCode.FORBIDDEN.getCode(), ResultCode.FORBIDDEN.getMessage()))
                )
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .httpBasic(AbstractHttpConfigurer::disable);
        return http.build();
    }

    private void writeUnified(HttpServletResponse response, int httpStatus, int bizCode, String message) throws IOException {
        response.setStatus(httpStatus);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(new ObjectMapper().writeValueAsString(R.fail(bizCode, message)));
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(Arrays.stream(corsAllowedOrigins.split(","))
                .map(String::trim).filter(s -> !s.isEmpty()).toList());
        configuration.setAllowedMethods(Arrays.stream(corsAllowedMethods.split(","))
                .map(String::trim).filter(s -> !s.isEmpty()).toList());
        configuration.setAllowedHeaders(Arrays.stream(corsAllowedHeaders.split(","))
                .map(String::trim).filter(s -> !s.isEmpty()).toList());
        configuration.setAllowCredentials(corsAllowCredentials);
        configuration.setMaxAge(corsMaxAge);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
