package com.dzgylxt.vo.common;

import lombok.Data;

import java.io.Serializable;

/**
 * 文件上传响应。
 *
 * <p>供商品主图（{@code imageFileKey}）与资质附件（{@code fileKey}）等场景复用。</p>
 */
@Data
public class FileUploadRespVO implements Serializable {

    /** 存储对象键（写入业务表 imageFileKey / fileKey 字段）。 */
    private String fileKey;

    /** 访问 URL（可能为 null，取决于存储配置）。 */
    private String url;

    /** 原始文件名。 */
    private String originalName;

    /** 文件字节数。 */
    private Long size;
}
