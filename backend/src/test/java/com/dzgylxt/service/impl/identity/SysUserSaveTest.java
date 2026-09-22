package com.dzgylxt.service.impl.identity;

import com.dzgylxt.common.GlobalExceptionHandler;
import com.dzgylxt.common.ParamException;
import com.dzgylxt.common.R;
import com.dzgylxt.common.ResultCode;
import com.dzgylxt.entity.identity.SysUser;
import com.dzgylxt.mapper.identity.SysUserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import org.junit.jupiter.api.Assertions;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 用户创建密码编码回归测试（P3-20）。
 *
 * <p>缺陷：{@code POST /api/v1/org/users} 经通用 {@code BaseController.save} 直接落库，
 * {@code password_hash} 为 NULL → MySQL 严格模式 500 / code 1000。
 * 修复后契约：</p>
 * <ol>
 *   <li>带密码创建 → {@code password_hash} 为 BCrypt 格式且 {@code matches(明文)}，库中无明文；</li>
 *   <li>缺密码 → HTTP 400 / code 4000（「密码不能为空」），不落库；</li>
 *   <li>admin/admin123 登录回归（种子哈希仍可匹配）。</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
class SysUserSaveTest {

    /** data.sql 中 admin 的种子哈希（密码 admin123），回归确保不被动。 */
    private static final String ADMIN_SEED_HASH =
            "$2a$10$pxjuW3BEyUH2EunQavbwWOTURXWJ/fLDpzVNrkKgjhsisXNhR8iwK";

    @Mock
    private SysUserMapper sysUserMapper;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    private SysUserServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new SysUserServiceImpl();
        // ServiceImpl.baseMapper 与 PasswordEncoder 在纯单测中不会被 Spring 注入，手动设置。
        ReflectionTestUtils.setField(service, "baseMapper", sysUserMapper);
        ReflectionTestUtils.setField(service, "passwordEncoder", passwordEncoder);
    }

    /** ① 带密码创建：password_hash 为 BCrypt 格式且 matches(明文)，实体上明文被清空。 */
    @Test
    void saveWithPassword_storesBcryptHashWithoutPlaintext() {
        SysUser entity = new SysUser();
        entity.setUsername("qa_user");
        entity.setPassword("User@123");
        when(sysUserMapper.insert(any(SysUser.class))).thenReturn(1);

        assertTrue(service.save(entity));

        ArgumentCaptor<SysUser> captor = ArgumentCaptor.forClass(SysUser.class);
        verify(sysUserMapper).insert(captor.capture());
        SysUser inserted = captor.getValue();

        String hash = inserted.getPasswordHash();
        assertNotNull(hash, "password_hash 必须写入");
        assertTrue(hash.startsWith("$2"), "password_hash 应为 BCrypt 格式，实际: " + hash);
        assertTrue(passwordEncoder.matches("User@123", hash), "哈希应能通过 matches(明文) 校验");
        assertNotEquals("User@123", hash, "绝不允许明文进哈希列");
        assertNull(inserted.getPassword(), "明文字段不得入库");
        assertNull(entity.getPassword(), "明文字段应在编码后清空");
    }

    /** ② 缺密码：HTTP 层经 GlobalExceptionHandler 返回 400 / code 4000，且不落库。 */
    @Test
    void saveWithoutPassword_throwsParamErrorAndSkipsInsert() {
        SysUser entity = new SysUser();
        entity.setUsername("qa_user");

        ParamException e = Assertions.assertThrows(ParamException.class, () -> service.save(entity));
        assertEquals(ResultCode.PARAM_ERROR.getCode(), e.getCode());
        assertEquals("密码不能为空", e.getMessage());
        verify(sysUserMapper, never()).insert(any(SysUser.class));
    }

    /** ② 补充：null 实体同样按参数错误处理。 */
    @Test
    void saveNullEntity_throwsParamError() {
        Assertions.assertThrows(ParamException.class, () -> service.save(null));
        verify(sysUserMapper, never()).insert(any(SysUser.class));
    }

    /** 兼容：客户端将明文误填进 passwordHash（非 $2 前缀）也应编码，绝不落明文。 */
    @Test
    void saveWithPlaintextInHashField_encodesIt() {
        SysUser entity = new SysUser();
        entity.setUsername("qa_user");
        entity.setPasswordHash("plain-pw-123");
        when(sysUserMapper.insert(any(SysUser.class))).thenReturn(1);

        assertTrue(service.save(entity));

        ArgumentCaptor<SysUser> captor = ArgumentCaptor.forClass(SysUser.class);
        verify(sysUserMapper).insert(captor.capture());
        String hash = captor.getValue().getPasswordHash();
        assertTrue(hash.startsWith("$2"));
        assertTrue(passwordEncoder.matches("plain-pw-123", hash));
        assertNotEquals("plain-pw-123", hash);
    }

    /** 客户端传入合法 BCrypt 哈希（$2 前缀）时视为已编码，原样保留。 */
    @Test
    void saveWithPrehashedValue_keepsAsIs() {
        String prehashed = passwordEncoder.encode("User@123");
        SysUser entity = new SysUser();
        entity.setUsername("qa_user");
        entity.setPasswordHash(prehashed);
        when(sysUserMapper.insert(any(SysUser.class))).thenReturn(1);

        assertTrue(service.save(entity));

        ArgumentCaptor<SysUser> captor = ArgumentCaptor.forClass(SysUser.class);
        verify(sysUserMapper).insert(captor.capture());
        assertEquals(prehashed, captor.getValue().getPasswordHash());
        assertTrue(passwordEncoder.matches("User@123", captor.getValue().getPasswordHash()));
    }

    /** ③ admin 登录回归：data.sql 种子哈希仍与 admin123 匹配（既有用户 hash 不动）。 */
    @Test
    void adminSeedHash_stillMatchesAdmin123() {
        assertTrue(passwordEncoder.matches("admin123", ADMIN_SEED_HASH),
                "admin 种子哈希必须保持可用");
    }

    /** ② HTTP 映射：ParamException 由 GlobalExceptionHandler 映射为 400 / code 4000 + traceId。 */
    @Test
    void paramException_mapsTo400WithCode4000() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new ProbeController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        mockMvc.perform(post("/probe/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"qa_user\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ResultCode.PARAM_ERROR.getCode()))
                .andExpect(jsonPath("$.message").value("密码不能为空"))
                .andExpect(jsonPath("$.traceId").exists());
    }

    /** HTTP 契约：handleParam 标注 400（区别于通用 handleBiz 的 200）。 */
    @Test
    void handlerContract_paramExceptionIsBadRequest() throws Exception {
        R<Void> r = new GlobalExceptionHandler().handleParam(new ParamException("密码不能为空"));

        assertEquals(ResultCode.PARAM_ERROR.getCode(), r.getCode());
        assertEquals(HttpStatus.BAD_REQUEST,
                org.springframework.core.annotation.AnnotatedElementUtils.findMergedAnnotation(
                        GlobalExceptionHandler.class.getMethod("handleParam", ParamException.class),
                        org.springframework.web.bind.annotation.ResponseStatus.class).value());
    }

    /** 触发缺密码异常的探针控制器。 */
    @RestController
    static class ProbeController {

        private final SysUserServiceImpl probeService = new SysUserServiceImpl() {
            @Override
            public boolean save(SysUser entity) {
                throw new ParamException("密码不能为空");
            }
        };

        @PostMapping("/probe/users")
        public R<Boolean> save(@RequestBody SysUser entity) {
            return R.ok(probeService.save(entity));
        }
    }
}
