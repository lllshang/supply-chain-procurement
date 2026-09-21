package com.dzgylxt.common.storage;

import com.dzgylxt.common.IdGenerator;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;

/**
 * 基于 MinIO 的文件存储实现。
 */
public class MinioFileStorage implements FileStorage {

    private final MinioClient minioClient;
    private final String bucket;
    private final String endpoint;

    public MinioFileStorage(MinioClient minioClient, String bucket, String endpoint) {
        this.minioClient = minioClient;
        this.bucket = bucket;
        this.endpoint = endpoint;
    }

    @Override
    public String upload(InputStream inputStream, String originalName, long size) throws IOException {
        String fileKey = IdGenerator.nextIdStr() + "-" + (originalName == null ? "file" : originalName);
        try {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(fileKey)
                    .stream(inputStream, size, -1)
                    .build());
        } catch (Exception e) {
            throw new IOException("MinIO 上传失败: " + e.getMessage(), e);
        }
        return fileKey;
    }

    /**
     * 便捷方法：直接接收 Spring MultipartFile。
     */
    public String upload(MultipartFile file) throws IOException {
        return upload(file.getInputStream(), file.getOriginalFilename(), file.getSize());
    }

    @Override
    public void delete(String fileKey) throws IOException {
        try {
            minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(bucket)
                    .object(fileKey)
                    .build());
        } catch (Exception e) {
            throw new IOException("MinIO 删除失败: " + e.getMessage(), e);
        }
    }

    @Override
    public String getUrl(String fileKey) {
        return endpoint + "/" + bucket + "/" + fileKey;
    }
}
