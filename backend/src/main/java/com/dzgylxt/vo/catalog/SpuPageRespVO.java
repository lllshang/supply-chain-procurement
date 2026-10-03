package com.dzgylxt.vo.catalog;

import com.dzgylxt.enums.ItemType;
import com.dzgylxt.enums.ProductStatus;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * SPU 分页响应。
 */
@Data
public class SpuPageRespVO implements Serializable {

    private Long id;
    private String spuCode;
    private String name;
    private Long categoryId;
    private String categoryName;
    /** 采购项类型：物料类/服务类 */
    private ItemType itemType;
    /** 规格包装类型：0=普通 1=固定混色箱 */
    private Integer packType;
    private String baseUnit;
    /** 基本单位显示名（unit.code → unit.name，未命中回退 code） */
    private String baseUnitName;
    private String imageFileKey;
    /** 主图访问 URL（由 fileKey 解析，存储不可用时为 null） */
    private String imageUrl;
    /** 0=正常 1=停用 */
    private ProductStatus status;
    /** 商品简介 */
    private String description;
    /** 税率%（目录域产品级，合法域 0–13） */
    private java.math.BigDecimal taxRate;
    private LocalDateTime updatedAt;

    // ===== 列表聚合字段（P-C5 对齐原型：规格/单位/标准价区间） =====
    /** SPU 级规格说明 */
    private String specification;
    /** SKU 数量 */
    private Integer skuCount;
    /** 采购单位（取首个 SKU 的采购单位，无 SKU 则为 null） */
    private String purchaseUnit;
    /** 采购单位显示名（unit.code → unit.name，未命中回退 code） */
    private String purchaseUnitName;
    /** 单位换算文本，如 "1袋=25kg"，无换算则为 null */
    private String unitConversion;
    /** 标准价区间下限（SKU 标准价最小，无 SKU 则为 null） */
    private java.math.BigDecimal standardPriceMin;
    /** 标准价区间上限（SKU 标准价最大，无 SKU 则为 null） */
    private java.math.BigDecimal standardPriceMax;
    /** 三级品类完整路径，如 "原材料 / 钢材 / 螺纹钢" */
    private String categoryPath;
}
