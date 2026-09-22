package com.dzgylxt.mapper.purchase;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dzgylxt.entity.purchase.PurchaseApplyItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/** 采购申请明细 Mapper（含下单余量扣减的行锁查询）。 */
@Mapper
public interface PurchaseApplyItemMapper extends BaseMapper<PurchaseApplyItem> {

    /**
     * 按申请单锁全部明细行（FOR UPDATE，设计 §4.2）。
     *
     * <p>锁序约定：全局统一 {@code contract → purchase_apply_item}，
     * 调用方必须先锁 contract 行再调用本方法，杜绝交叉死锁。</p>
     */
    @Select("SELECT * FROM purchase_apply_item WHERE deleted = 0 AND apply_id = #{applyId} FOR UPDATE")
    List<PurchaseApplyItem> selectForUpdateByApply(@Param("applyId") Long applyId);

    /**
     * 按明细 ID 集合锁行（FOR UPDATE，本单涉及的申请明细行）。
     *
     * <p>对 IN 列表先排序后加锁，保证多线程对同一批明细的加锁顺序一致。</p>
     */
    @Select("<script>SELECT * FROM purchase_apply_item WHERE deleted = 0 AND id IN"
            + " <foreach collection='ids' item='id' open='(' separator=',' close=')' >#{id}</foreach>"
            + " ORDER BY id FOR UPDATE</script>")
    List<PurchaseApplyItem> selectForUpdateByIds(@Param("ids") List<Long> ids);
}
