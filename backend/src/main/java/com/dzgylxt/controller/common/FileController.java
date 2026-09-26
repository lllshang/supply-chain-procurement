package com.dzgylxt.controller.common;

import com.dzgylxt.common.BizException;
import com.dzgylxt.common.R;
import com.dzgylxt.common.ResultCode;
import com.dzgylxt.common.storage.FileStorage;
import com.dzgylxt.entity.common.FileMeta;
import com.dzgylxt.mapper.common.FileMetaMapper;
import com.dzgylxt.vo.common.FileUploadRespVO;
import lombok.extern.slf4j.Slf4j;
import com.dzgylxt.common.SecurityConstants;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * 通用文件上传端点。
 *
 * <p>供商品主图（{@code imageFileKey}）与资质附件（{@code fileKey}）使用：统一走
 * {@link FileStorage}（一期 {@code MinioFileStorage}）落存储，返回 {@code fileKey} 供业务表引用。</p>
 *
 * <p>可用性兜底：MinIO 不可用等存储异常统一转为 {@link BizException}（业务错误码），
 * <b>不返回 500</b>，便于前端联调时展示明确提示。</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/files")
public class FileController {


    private final FileStorage fileStorage;
    private final FileMetaMapper fileMetaMapper;

    public FileController(FileStorage fileStorage, FileMetaMapper fileMetaMapper) {
        this.fileStorage = fileStorage;
        this.fileMetaMapper = fileMetaMapper;
    }

    /**
     * 上传文件（multipart/form-data）。
     *
     * @param file 表单文件字段名固定为 {@code file}
     * @return 统一响应体，{@code data} 含 {@code fileKey}（及可选 {@code url}）
     */
    @PreAuthorize(SecurityConstants.GUARD)
    @PostMapping("/upload")
    public R<FileUploadRespVO> upload(@RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BizException(ResultCode.PARAM_ERROR, "上传文件为空");
        }
        String fileKey;
        try {
            fileKey = fileStorage.upload(file.getInputStream(), file.getOriginalFilename(), file.getSize());
        } catch (IOException | RuntimeException e) {
            // MinIO 不可达 / IO 异常等：转为明确业务错误（1002），避免冒泡为 500
            log.warn("文件上传失败（存储不可用或 IO 异常）: {}", e.getMessage());
            throw new BizException(ResultCode.SERVICE_UNAVAILABLE, "文件存储不可用，请稍后重试");
        }

        String url = null;
        try {
            url = fileStorage.getUrl(fileKey);
        } catch (RuntimeException e) {
            log.warn("获取文件 URL 失败（不影响上传结果）: {}", e.getMessage());
        }

        registerMeta(fileKey, file);

        FileUploadRespVO vo = new FileUploadRespVO();
        vo.setFileKey(fileKey);
        vo.setUrl(url);
        vo.setOriginalName(file.getOriginalFilename());
        vo.setSize(file.getSize());
        return R.ok(vo);
    }

    /** 登记文件元数据（file_meta）；失败仅告警，不影响上传结果。 */
    private void registerMeta(String fileKey, MultipartFile file) {
        try {
            FileMeta meta = new FileMeta();
            meta.setFileKey(fileKey);
            meta.setOriginalName(file.getOriginalFilename());
            meta.setSize(file.getSize());
            fileMetaMapper.insert(meta);
        } catch (RuntimeException e) {
            log.warn("文件元数据登记失败（不影响上传）: {}", e.getMessage());
        }
    }
}
