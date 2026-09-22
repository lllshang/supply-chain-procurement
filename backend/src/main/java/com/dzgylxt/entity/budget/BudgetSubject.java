package com.dzgylxt.entity.budget;

import com.baomidou.mybatisplus.annotation.TableName;
import com.dzgylxt.common.BaseEntity;
import com.dzgylxt.enums.SubjectType;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;

/**
 * 预算科目（支持父子层级）。
 *
 * <p>{@code budget_line.subject_id} 引用之；被引用的科目不可删，仅可置 {@code status=1}。</p>
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("budget_subject")
public class BudgetSubject extends BaseEntity implements Serializable {

    /** 科目编码（有效期内唯一） */
    private String code;
    /** 科目名称 */
    private String name;
    /** 父科目ID，0=根 */
    private Long parentId;
    /** 科目类型：1=支出 2=收入 */
    private SubjectType subjectType;
    /** 0=有效，1=无效 */
    private Integer status;
}
