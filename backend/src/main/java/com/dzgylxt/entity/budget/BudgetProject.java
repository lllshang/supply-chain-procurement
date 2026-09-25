package com.dzgylxt.entity.budget;

import com.baomidou.mybatisplus.annotation.TableName;
import com.dzgylxt.common.BaseEntity;
import com.dzgylxt.enums.CatalogStatus;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;

/**
 * 预算项目（可选维度）。
 *
 * <p>启用项目维度时，{@code budget_line.project_id} 引用之（nullable）。</p>
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("budget_project")
public class BudgetProject extends BaseEntity implements Serializable {

    /** 项目编码（有效期内唯一） */
    private String code;
    /** 项目名称 */
    private String name;
    /** 所属年份，如 2026 */
    private Integer year;
    /** 0=有效，1=无效 */
    private CatalogStatus status;
}
