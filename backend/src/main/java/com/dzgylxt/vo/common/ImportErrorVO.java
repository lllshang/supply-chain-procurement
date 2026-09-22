package com.dzgylxt.vo.common;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 导入/校验错误定位（行 / 列 / 消息）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ImportErrorVO implements Serializable {

    /** 行号（从 1 开始，含表头为 1；数据行从 2 起） */
    private Integer row;
    /** 列名或列索引 */
    private String col;
    /** 错误消息 */
    private String msg;
}
