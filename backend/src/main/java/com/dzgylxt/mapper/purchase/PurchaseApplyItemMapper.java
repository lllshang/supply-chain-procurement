package com.dzgylxt.mapper.purchase;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dzgylxt.entity.purchase.PurchaseApplyItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
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
     * <p>注意：此处<b>不可写 {@code ORDER BY ... FOR UPDATE}</b>——项目 SQL 拦截器
     * （JSqlParser）会重排为 {@code FOR UPDATE ORDER BY ...} 导致语法错误。
     * 主键 IN 扫描本身按主键升序加锁，天然保证多线程锁序一致；
     * 调用方须传入排序去重后的 ID 列表（见 OrderServiceImpl）。</p>
     */
    @Select("<script>SELECT * FROM purchase_apply_item WHERE deleted = 0 AND id IN"
            + " <foreach collection='ids' item='id' open='(' separator=',' close=')' >#{id}</foreach>"
            + " FOR UPDATE</script>")
    List<PurchaseApplyItem> selectForUpdateByIds(@Param("ids") List<Long> ids);

    /**
     * 条件扣减申请余量（乐观版本兜底，主防=行锁）。
     *
     * <p>SQL 条件 {@code version=? AND remain_qty >= qty} 双重保护：
     * 行锁遗漏的极端场景下不超量，返回 0 行表示冲突/不足。</p>
     */
    @Update("UPDATE purchase_apply_item SET ordered_qty = ordered_qty + #{qty},"
            + " remain_qty = remain_qty - #{qty}, version = version + 1"
            + " WHERE id = #{id} AND version = #{version} AND remain_qty >= #{qty}")
    int deductRemain(@Param("id") Long id,
                     @Param("qty") BigDecimal qty,
                     @Param("version") Integer version);

    /** 回冲申请余量（订单取消/变更差额，乐观版本兜底）。 */
    @Update("UPDATE purchase_apply_item SET ordered_qty = ordered_qty - #{qty},"
            + " remain_qty = remain_qty + #{qty}, version = version + 1"
            + " WHERE id = #{id} AND version = #{version}")
    int restoreRemain(@Param("id") Long id,
                      @Param("qty") BigDecimal qty,
                      @Param("version") Integer version);
}
