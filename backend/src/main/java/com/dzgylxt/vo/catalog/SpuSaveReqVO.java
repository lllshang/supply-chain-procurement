package com.dzgylxt.vo.catalog;

import com.dzgylxt.enums.ItemType;
import com.dzgylxt.enums.ProductStatus;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * SPU 新增/编辑请求（全量对齐原型编辑对话框：单/多规格 + 固定混色箱）。
 */
@Data
public class SpuSaveReqVO implements Serializable {

    private String spuCode;
    private String name;
    /** 三级品类（必须为叶子节点） */
    private Long categoryId;
    /** 采购项类型：物料类/服务类（缺省物料类） */
    private ItemType itemType;
    /** 规格包装类型：0=普通 1=固定混色箱（缺省普通；由 specificationMode 推导，保持兼容） */
    private Integer packType;
    private String spec;
    /** 基本单位（引用 unit.code） */
    private String baseUnit;
    private String imageFileKey;
    private String description;
    /** 税率%（目录域产品级，合法域 0–13） */
    private BigDecimal taxRate;
    private String remark;
    /** 0=正常 1=停用 */
    private ProductStatus status;

    // ===== 编辑对话框全量对齐原型 =====
    /** 产品条码（单规格/混色箱模式录入） */
    private String barcode;
    /** 计量方式：piece=计件 weight=计重 */
    private String measurementType;
    /** 采购单位（引用 unit.code） */
    private String purchaseUnit;
    /** 规格模式：single=单规格 multiple=多规格 mixed=固定混色箱 */
    private String specificationMode;
    /** 单位换算系数：1 采购单位 = N 基本单位（单规格/混色箱模式） */
    private Integer unitConversionFactor;
    /** 标准价（SPU 级，单规格/混色箱模式；元/采购单位） */
    private BigDecimal standardPrice;
    /** 参考价（SPU 级，单规格/混色箱模式；元/采购单位） */
    private BigDecimal referencePrice;
    /** 固定混色箱子件（mixed 模式）：[{name, skuCode, specification, quantity, baseUnit}] */
    private List<MixedPackComponentDTO> mixedPackComponents;
    /** 多规格属性定义（multiple 模式）：[{id, name, values[]}] */
    private List<SpecAttributeDTO> specAttributes;

    /** 固定混色箱子件。 */
    @Data
    public static class MixedPackComponentDTO implements Serializable {
        private String name;
        private String skuCode;
        private String specification;
        private Integer quantity;
        private String baseUnit;
    }

    /** 多规格属性定义（规格类型 + 可选参数值）。 */
    @Data
    public static class SpecAttributeDTO implements Serializable {
        private String id;
        private String name;
        private List<String> values;
    }
}
