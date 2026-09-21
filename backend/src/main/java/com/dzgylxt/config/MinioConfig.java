package com.dzgylxt.config;

import com.dzgylxt.common.storage.FileStorage;
import com.dzgylxt.common.storage.MinioFileStorage;
import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MinIO 配置：客户端 + FileStorage 实现。
 *
 * <p>启动期对 MinIO 的连通性做<b>容错</b>处理（P2-10）：若 MinIO 不可达，
 * 仅记录告警并降级为「存储不可用」，<b>不影响主流程启动</b>。实际文件上传业务在阶段二补齐，
 * 届时若 MinIO 仍不可用，上传接口应返回明确错误而非拖垮整体。</p>
 */
@Slf4j
@Configuration
public class MinioConfig {

    @Value("${app.minio.endpoint}")
    private String endpoint;

    @Value("${app.minio.access-key}")
    private String accessKey;

    @Value("${app.minio.secret-key}")
    private String secretKey;

    @Value("${app.minio.bucket:supply-chain}")
    private String bucket;

    @Bean
    public MinioClient minioClient() {
        return MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .build();
    }

    @Bean
    public FileStorage fileStorage(MinioClient minioClient) {
        try {
            if (!minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build())) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
            }
        } catch (Exception e) {
            log.warn("MinIO 存储初始化失败（bucket 检查/创建异常），已降级为不可用，不影响主流程启动: {}", e.getMessage());
        }
        return new MinioFileStorage(minioClient, bucket, endpoint);
    }
}
