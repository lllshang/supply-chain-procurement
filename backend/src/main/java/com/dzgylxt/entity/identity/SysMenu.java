package com.dzgylxt.entity.identity;

import com.dzgylxt.common.BaseEntity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.dzgylxt.enums.MenuType;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;

/**
 * 菜单 / 按钮（动态菜单 + 权限标识来源）。
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("sys_menu")
public class SysMenu extends BaseEntity implements Serializable {

    private Long parentId;
    private String menuName;
    private MenuType menuType;
    /** 路由 path */
    private String path;
    /** 前端组件路径 */
    private String component;
    private String icon;
    /** 权限标识 res:action（逗号分隔） */
    private String perms;
    private Integer sort;
    /** 0=显示，1=隐藏 */
    private Integer status;
}
