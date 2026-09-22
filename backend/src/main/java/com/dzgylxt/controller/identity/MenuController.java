package com.dzgylxt.controller.identity;

import com.dzgylxt.common.R;
import com.dzgylxt.entity.identity.SysMenu;
import com.dzgylxt.permission.RbacService;
import com.dzgylxt.security.UserContext;
import com.dzgylxt.vo.identity.MenuTreeVO;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 动态菜单接口：按当前登录用户角色返回菜单树。
 */
@RestController
@RequestMapping("/api/v1")
public class MenuController {

    @Autowired
    private RbacService rbacService;

    @GetMapping("/menu")
    public R<List<MenuTreeVO>> menu() {
        Long userId = UserContext.getCurrentUserId();
        if (userId == null) {
            return R.fail(com.dzgylxt.common.ResultCode.UNAUTHORIZED);
        }
        List<SysMenu> menus = rbacService.getMenusByUser(userId);
        List<MenuTreeVO> vos = menus.stream().map(m -> {
            MenuTreeVO vo = new MenuTreeVO();
            BeanUtils.copyProperties(m, vo);
            return vo;
        }).toList();
        return R.ok(buildTree(vos));
    }

    private List<MenuTreeVO> buildTree(List<MenuTreeVO> all) {
        Map<Long, MenuTreeVO> map = all.stream()
                .collect(Collectors.toMap(MenuTreeVO::getId, v -> v));
        List<MenuTreeVO> roots = new ArrayList<>();
        for (MenuTreeVO vo : all) {
            if (vo.getParentId() == null || vo.getParentId() == 0L) {
                roots.add(vo);
            } else {
                MenuTreeVO parent = map.get(vo.getParentId());
                if (parent != null) {
                    if (parent.getChildren() == null) {
                        parent.setChildren(new ArrayList<>());
                    }
                    parent.getChildren().add(vo);
                } else {
                    roots.add(vo);
                }
            }
        }
        return roots;
    }
}
