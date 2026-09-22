package com.dzgylxt.controller.common;

import com.dzgylxt.common.BizException;
import com.dzgylxt.common.R;
import com.dzgylxt.common.ResultCode;
import com.dzgylxt.common.storage.FileStorage;
import com.dzgylxt.mapper.common.FileMetaMapper;
import com.dzgylxt.vo.common.FileUploadRespVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

/**
 * 文件上传端点单元测试（前置 R1）。
 *
 * <p>覆盖：正常上传返回 fileKey/url；存储不可用（MinIO 异常）转为业务错误而非 500；空文件参数校验。</p>
 */
@ExtendWith(MockitoExtension.class)
class FileControllerTest {

    @Mock
    private FileStorage fileStorage;

    @Mock
    private FileMetaMapper fileMetaMapper;

    @InjectMocks
    private FileController controller;

    @Test
    void upload_returnsFileKeyAndUrl() throws IOException {
        when(fileStorage.upload(any(), any(), anyLong())).thenReturn("KEY-123");
        when(fileStorage.getUrl("KEY-123")).thenReturn("http://minio/supply-chain/KEY-123");
        MockMultipartFile file = new MockMultipartFile("file", "a.png", "image/png", "x".getBytes());

        R<FileUploadRespVO> result = controller.upload(file);

        assertEquals(ResultCode.SUCCESS.getCode(), result.getCode());
        assertNotNull(result.getData());
        assertEquals("KEY-123", result.getData().getFileKey());
        assertEquals("http://minio/supply-chain/KEY-123", result.getData().getUrl());
        assertEquals("a.png", result.getData().getOriginalName());
    }

    @Test
    void upload_storageUnavailable_returnsBizErrorNot500() throws IOException {
        when(fileStorage.upload(any(), any(), anyLong()))
                .thenThrow(new IOException("MinIO 上传失败: connection refused"));
        MockMultipartFile file = new MockMultipartFile("file", "a.png", "image/png", "x".getBytes());

        BizException ex = assertThrows(BizException.class, () -> controller.upload(file));

        assertEquals(ResultCode.SERVICE_UNAVAILABLE.getCode(), ex.getCode());
    }

    @Test
    void upload_emptyFile_throwsParamError() {
        MockMultipartFile file = new MockMultipartFile("file", "a.png", "image/png", new byte[0]);

        BizException ex = assertThrows(BizException.class, () -> controller.upload(file));

        assertEquals(ResultCode.PARAM_ERROR.getCode(), ex.getCode());
    }
}
