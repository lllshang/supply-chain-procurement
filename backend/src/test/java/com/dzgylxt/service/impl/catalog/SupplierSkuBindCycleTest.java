package com.dzgylxt.service.impl.catalog;

import com.dzgylxt.entity.catalog.Sku;
import com.dzgylxt.entity.catalog.Supplier;
import com.dzgylxt.entity.catalog.SupplierSku;
import com.dzgylxt.mapper.catalog.SkuMapper;
import com.dzgylxt.mapper.catalog.SupplierMapper;
import com.dzgylxt.mapper.catalog.SupplierSkuMapper;
import com.dzgylxt.service.ISkuService;
import com.dzgylxt.service.IUnitService;
import com.dzgylxt.vo.supplier.SupplierSkuSaveReqVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * P3-8 回归测试：同一 (supplierId, skuId) 连续「绑定 → 解绑」≥3 轮均成功。
 *
 * <p>以内存模型精确模拟唯一约束 {@code uk_sup_sku(supplier_id, sku_id, deleted)} 与逻辑删除语义
 * （{@code deleted} 为常量删除值 1）：若 {@code unbind} 未在逻辑删除前物理清理已存在的
 * {@code deleted=1} 历史行，则第 2 轮解绑即会抛 {@link DuplicateKeyException}。本测试即验证该加固。</p>
 */
@ExtendWith(MockitoExtension.class)
class SupplierSkuBindCycleTest {

    private static final long SUPPLIER_ID = 1L;
    private static final long SKU_ID = 2L;

    @Mock
    private SupplierMapper supplierMapper;
    @Mock
    private SkuMapper skuMapper;
    @Mock
    private ISkuService skuService;
    @Mock
    private IUnitService unitService;
    @Mock
    private SupplierSkuMapper supplierSkuMapper;

    private SupplierSkuServiceImpl service;
    private InMemorySupplierSkuStore store;

    @BeforeEach
    void setUp() {
        store = new InMemorySupplierSkuStore();
        service = new SupplierSkuServiceImpl(supplierMapper, skuMapper, skuService, unitService);
        ReflectionTestUtils.setField(service, "baseMapper", supplierSkuMapper);

        Supplier supplier = new Supplier();
        supplier.setId(SUPPLIER_ID);
        when(supplierMapper.selectById(SUPPLIER_ID)).thenReturn(supplier);
        Sku sku = new Sku();
        sku.setId(SKU_ID);
        when(skuService.getById(SKU_ID)).thenReturn(sku);

        when(supplierSkuMapper.insert(any(SupplierSku.class)))
                .thenAnswer(inv -> store.insert(inv.getArgument(0)));
        when(supplierSkuMapper.selectById(any(Serializable.class)))
                .thenAnswer(inv -> store.get(inv.getArgument(0)));
        when(supplierSkuMapper.deleteById(any(Serializable.class)))
                .thenAnswer(inv -> store.logicalDelete(inv.getArgument(0)));
        when(supplierSkuMapper.physicalDeleteDeletedByPair(any(), any()))
                .thenAnswer(inv -> store.physicalDelete(inv.getArgument(0), inv.getArgument(1)));
        when(supplierSkuMapper.existsBind(any(), any(), any()))
                .thenAnswer(inv -> store.existsBind(inv.getArgument(0), inv.getArgument(1), inv.getArgument(2)));
    }

    @Test
    void bindUnbind_threeRoundCycle_alwaysSucceeds() {
        for (int round = 1; round <= 3; round++) {
            SupplierSkuSaveReqVO req = new SupplierSkuSaveReqVO();
            req.setSupplierId(SUPPLIER_ID);
            req.setSkuId(SKU_ID);

            Long id = service.bind(req);
            assertNotNull(id, "第 " + round + " 轮绑定应返回 id");
            assertEquals(1, activeRowCount(), "第 " + round + " 轮：同一 (supplier,sku) 应有且仅有 1 条有效行");

            service.unbind(id);
            assertTrue(deletedRowCount() <= 1,
                    "第 " + round + " 轮：同一 (supplier,sku) 至多保留 1 条已删行，实际=" + deletedRowCount());
        }
    }

    private long activeRowCount() {
        return store.rows.values().stream()
                .filter(r -> Integer.valueOf(0).equals(r.getDeleted()))
                .count();
    }

    private long deletedRowCount() {
        return store.rows.values().stream()
                .filter(r -> Integer.valueOf(1).equals(r.getDeleted()))
                .count();
    }

    /** 内存模型：模拟 uk_sup_sku(supplier_id, sku_id, deleted) 唯一约束 + 逻辑删除。 */
    static final class InMemorySupplierSkuStore {
        final Map<Long, SupplierSku> rows = new LinkedHashMap<>();
        private final AtomicLong seq = new AtomicLong(1000);

        int insert(SupplierSku entity) {
            if (entity.getId() == null) {
                entity.setId(seq.incrementAndGet());
            }
            if (entity.getDeleted() == null) {
                entity.setDeleted(0);
            }
            if (hasSamePairAndDeleted(entity.getSupplierId(), entity.getSkuId(),
                    entity.getDeleted(), entity.getId())) {
                throw new DuplicateKeyException("uk_sup_sku duplicate on insert");
            }
            rows.put(entity.getId(), entity);
            return 1;
        }

        SupplierSku get(Long id) {
            return rows.get(id);
        }

        int logicalDelete(Long id) {
            SupplierSku row = rows.get(id);
            if (row == null) {
                return 0;
            }
            if (hasSamePairAndDeleted(row.getSupplierId(), row.getSkuId(), 1, id)) {
                throw new DuplicateKeyException("uk_sup_sku duplicate on logical delete");
            }
            row.setDeleted(1);
            return 1;
        }

        int physicalDelete(Long supplierId, Long skuId) {
            List<Long> ids = rows.values().stream()
                    .filter(r -> Objects.equals(r.getSupplierId(), supplierId)
                            && Objects.equals(r.getSkuId(), skuId)
                            && Integer.valueOf(1).equals(r.getDeleted()))
                    .map(SupplierSku::getId)
                    .toList();
            ids.forEach(rows::remove);
            return ids.size();
        }

        boolean existsBind(Long supplierId, Long skuId, Long excludeId) {
            return rows.values().stream().anyMatch(r ->
                    Objects.equals(r.getSupplierId(), supplierId)
                            && Objects.equals(r.getSkuId(), skuId)
                            && Integer.valueOf(0).equals(r.getDeleted())
                            && !Objects.equals(r.getId(), excludeId));
        }

        private boolean hasSamePairAndDeleted(Long supplierId, Long skuId, Integer deleted, Long excludeId) {
            return rows.values().stream().anyMatch(r ->
                    Objects.equals(r.getSupplierId(), supplierId)
                            && Objects.equals(r.getSkuId(), skuId)
                            && Objects.equals(r.getDeleted(), deleted)
                            && !Objects.equals(r.getId(), excludeId));
        }
    }
}
