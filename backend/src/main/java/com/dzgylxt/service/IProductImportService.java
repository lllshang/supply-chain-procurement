package com.dzgylxt.service;

import com.dzgylxt.vo.catalog.ProductExportReqVO;
import com.dzgylxt.vo.catalog.ProductImportPreviewVO;
import com.dzgylxt.vo.common.ImportTaskVO;
import org.springframework.web.multipart.MultipartFile;

/**
 * 产品导入导出服务（R-PRD-10/11/12）。
 */
public interface IProductImportService {

    /** 单规格导入（校验 + 错误 Sheet）。 */
    ImportTaskVO importSingle(MultipartFile file);

    /** 多规格预览（不落库，错误定位到行/列）。 */
    ProductImportPreviewVO previewMulti(MultipartFile file);

    /** 多规格导入。 */
    ImportTaskVO importMulti(MultipartFile file);

    /** 筛选导出（异步 task_id）。 */
    ImportTaskVO export(ProductExportReqVO req, String templateVersion);

    /** 导出任务进度。 */
    ImportTaskVO exportTaskStatus(String taskId);

    /** 取导出文件字节（下载）。 */
    byte[] exportBytes(String taskId);
}
