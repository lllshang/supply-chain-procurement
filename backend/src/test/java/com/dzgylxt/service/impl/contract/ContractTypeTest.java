package com.dzgylxt.service.impl.contract;

import com.dzgylxt.common.BizException;
import com.dzgylxt.entity.contract.ContractType;
import com.dzgylxt.mapper.contract.ContractTypeMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * P4 R3a 合同类型字典单测（PRD L840：被引用不可删）：
 * type_code 唯一 / 编辑启停 / 引用计数 >0 删除拒绝（3003）/ 未引用可删。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ContractTypeTest {

    @Mock
    private ContractTypeMapper contractTypeMapper;

    private ContractTypeServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ContractTypeServiceImpl();
        ReflectionTestUtils.setField(service, "baseMapper", contractTypeMapper);
    }

    @Test
    void create_duplicateCode_conflict() {
        when(contractTypeMapper.selectCount(any())).thenReturn(1L);
        ContractType type = new ContractType();
        type.setTypeCode("MATERIAL");
        type.setTypeName("物料");

        BizException e = assertThrows(BizException.class, () -> service.createType(type));
        assertEquals(3002, e.getCode());
    }

    @Test
    void create_blankParams_paramError() {
        ContractType type = new ContractType();
        type.setTypeCode(" ");

        BizException e = assertThrows(BizException.class, () -> service.createType(type));
        assertEquals(4000, e.getCode());
    }

    /** PRD L840：被引用不可删 → 3003。 */
    @Test
    void delete_referenced_rejected() {
        ContractType type = new ContractType();
        type.setId(1L);
        type.setTypeCode("MATERIAL");
        when(contractTypeMapper.selectById(1L)).thenReturn(type);
        when(contractTypeMapper.countContractRef(1L)).thenReturn(3L);

        BizException e = assertThrows(BizException.class, () -> service.deleteType(1L));
        assertEquals(3003, e.getCode());
        assertTrue(e.getMessage().contains("不可删除"));
    }

    @Test
    void delete_unreferenced_removed() {
        ContractType type = new ContractType();
        type.setId(1L);
        when(contractTypeMapper.selectById(1L)).thenReturn(type);
        when(contractTypeMapper.countContractRef(1L)).thenReturn(0L);
        when(contractTypeMapper.deleteById(1L)).thenReturn(1);

        service.deleteType(1L);
        org.mockito.Mockito.verify(contractTypeMapper).deleteById(1L);
    }

    @Test
    void list_implDelegation() {
        when(contractTypeMapper.selectList(any())).thenReturn(List.of(new ContractType()));
        assertEquals(1, service.list().size());
    }
}
