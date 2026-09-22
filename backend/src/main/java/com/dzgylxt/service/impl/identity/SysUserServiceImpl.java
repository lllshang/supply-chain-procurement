package com.dzgylxt.service.impl.identity;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dzgylxt.common.ParamException;
import com.dzgylxt.entity.identity.SysUser;
import com.dzgylxt.mapper.identity.SysUserMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 用户服务。
 *
 * <p>P3-20：此前 {@code POST /api/v1/org/users} 经通用 {@code BaseController.save}
 * 直接落库，{@code password_hash} 为 NULL → MySQL 严格模式报错返回 500/1000。
 * 现覆写 {@link #save(SysUser)} 统一收口创建逻辑：</p>
 * <ol>
 *   <li>密码使用与登录校验同一个 {@link PasswordEncoder}（BCrypt，见
 *       {@code SecurityConfig#passwordEncoder}）编码后写入 {@code password_hash}，绝不落明文；</li>
 *   <li>缺密码抛 {@link ParamException}（HTTP 400 / code 4000「密码不能为空」），不落库；</li>
 *   <li>update 路径本次不改（不支持经 update 改密码）。</li>
 * </ol>
 */
@Service
public class SysUserServiceImpl extends ServiceImpl<SysUserMapper, SysUser> {

    /** BCrypt 哈希固定前缀，用于区分「客户端传哈希」与「客户端误传明文」。 */
    private static final String BCRYPT_PREFIX = "$2";

    @Autowired
    private PasswordEncoder passwordEncoder;

    /**
     * 创建用户：保证 {@code password_hash} 永远为 BCrypt 编码结果（或已是 BCrypt 哈希）。
     *
     * <p>明文来源优先级：① 请求体 {@code password} 字段；② 兼容客户端将明文
     * 误填到 {@code passwordHash} 的情况（非 {@code $2} 前缀视为明文，同样编码）；
     * ③ {@code passwordHash} 已是 {@code $2} 前缀的 BCrypt 哈希时视为已编码，原样保留。
     * 三者均缺省时抛 {@link ParamException}，不落库（避免创建无法登录的用户）。</p>
     */
    @Override
    public boolean save(SysUser entity) {
        if (entity == null) {
            throw new ParamException("用户信息不能为空");
        }
        String rawPassword = entity.getPassword();
        if (!StringUtils.hasText(rawPassword)) {
            String providedHash = entity.getPasswordHash();
            if (StringUtils.hasText(providedHash) && providedHash.startsWith(BCRYPT_PREFIX)) {
                // 客户端直接传 BCrypt 哈希：视为已编码，原样保留（$2 前缀必非明文）
                entity.setPassword(null);
                return super.save(entity);
            }
            // 非 $2 前缀视为明文误填，走统一编码；两者均空则缺密码
            rawPassword = providedHash;
        }
        if (!StringUtils.hasText(rawPassword)) {
            throw new ParamException("密码不能为空");
        }
        // 明文清空（exist=false 本就不映射，双保险防回显/误落库），哈希列只存 BCrypt 编码结果
        entity.setPassword(null);
        entity.setPasswordHash(passwordEncoder.encode(rawPassword));
        return super.save(entity);
    }
}
