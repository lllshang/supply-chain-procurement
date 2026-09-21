package com.dzgylxt.common.storage;

import java.io.IOException;
import java.io.InputStream;

/**
 * 文件存储抽象接口（一期实现 MinIO，后续可切换云 OSS 不改业务代码）。
 */
public interface FileStorage {

    /**
     * 上传文件，返回 fileKey。
     */
    String upload(InputStream inputStream, String originalName, long size) throws IOException;

    /**
     * 删除文件。
     */
    void delete(String fileKey) throws IOException;

    /**
     * 获取访问 URL。
     */
    String getUrl(String fileKey);
}
