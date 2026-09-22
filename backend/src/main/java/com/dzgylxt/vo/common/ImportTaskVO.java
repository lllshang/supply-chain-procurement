package com.dzgylxt.vo.common;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 异步导入/导出任务进度与结果。
 *
 * <p>{@code status}：RUNNING / SUCCESS / FAILED。</p>
 */
@Data
public class ImportTaskVO implements Serializable {

    private String taskId;
    /** RUNNING / SUCCESS / FAILED */
    private String status;
    /** 是否全部成功 */
    private Boolean success;
    /** 总行数 */
    private Integer totalRows;
    /** 失败行数 */
    private Integer errorRows;
    /** 错误清单（定位到行/列） */
    private List<ImportErrorVO> errors = new ArrayList<>();
    /** 导出文件名（导出任务使用） */
    private String fileName;
    /** 错误清单文件 file_key（如需落盘，预留） */
    private String errorFileKey;
}
