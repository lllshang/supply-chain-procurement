package com.dzgylxt.vo.common;

import lombok.Data;

import com.dzgylxt.enums.CatalogStatus;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 通用树节点 VO（商品品类 / 供应商分类 / 预算科目复用）。
 */
@Data
public class CategoryTreeNodeVO implements Serializable {

    private Long id;
    private Long parentId;
    private String code;
    private String name;
    private Integer level;
    private CatalogStatus status;
    /** 子节点（叶子为空列表） */
    private List<CategoryTreeNodeVO> children = new ArrayList<>();
}
