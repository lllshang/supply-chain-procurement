package com.dzgylxt.entity.common;

import com.dzgylxt.common.BaseEntity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;

/**
 * 文件元数据（与 MinIO 对应，DB 仅存 key + 元数据）。
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("file_meta")
public class FileMeta extends BaseEntity implements Serializable {

    private String fileKey;
    private String originalName;
    private Long size;
    private String sha256;
    private Long bizId;
    private String bizType;
}
