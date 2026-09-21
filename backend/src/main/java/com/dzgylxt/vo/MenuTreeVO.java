package com.dzgylxt.vo;

import com.dzgylxt.enums.MenuType;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 动态菜单树节点 VO。
 */
@Data
public class MenuTreeVO implements Serializable {

    private Long id;
    private Long parentId;
    private String menuName;
    private MenuType menuType;
    private String path;
    private String component;
    private String icon;
    private String perms;
    private Integer sort;
    private List<MenuTreeVO> children;
}
