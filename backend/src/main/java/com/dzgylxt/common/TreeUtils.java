package com.dzgylxt.common;

import com.dzgylxt.vo.common.CategoryTreeNodeVO;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 通用树构建工具：由扁平节点列表构建父子嵌套树。
 */
public final class TreeUtils {

    private TreeUtils() {
    }

    /**
     * 将扁平节点构建为嵌套树。
     *
     * <p>父节点为 0 或指向不存在的节点时，作为根节点返回。</p>
     */
    public static List<CategoryTreeNodeVO> build(List<CategoryTreeNodeVO> nodes) {
        Map<Long, CategoryTreeNodeVO> map = new LinkedHashMap<>();
        for (CategoryTreeNodeVO node : nodes) {
            map.put(node.getId(), node);
        }
        List<CategoryTreeNodeVO> roots = new ArrayList<>();
        for (CategoryTreeNodeVO node : nodes) {
            Long parentId = node.getParentId();
            if (parentId == null || parentId == 0L || !map.containsKey(parentId)) {
                roots.add(node);
            } else {
                map.get(parentId).getChildren().add(node);
            }
        }
        return roots;
    }
}
