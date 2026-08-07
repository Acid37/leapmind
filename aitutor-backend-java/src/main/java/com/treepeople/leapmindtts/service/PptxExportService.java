package com.treepeople.leapmindtts.service;

import com.treepeople.leapmindtts.pojo.dto.PptStructureDTO;
import com.treepeople.leapmindtts.pojo.entity.TeachingContent;

/**
 * [跨端对齐] PPT 导出服务接口。
 *
 * 完整链路：PPT结构 JSON → Apache POI 生成 .pptx → 上传 MinIO → 返回下载 URL
 *
 * 格式约定：所有需 Java 消费并生成 PPT 的 JSON 必须先经 Python 端
 * lesson_prep_api.py L105-L109 convert_keys_camel() 处理，确保键名已是 camelCase。
 */
public interface PptxExportService {

    /**
     * 导出备课内容为PPTX文件
     *
     * @param prepId 备课ID
     * @return 下载URL
     */
    /** 根据备课ID导出 PPTX（完整链路：DB → POI → MinIO → URL） */
    String export(Long prepId);
        /** 从已查询到的 TeachingContent 实体导出（读 ppt_structure 列） */
    String exportFromTeachingContent(TeachingContent content);

    /** [跨端兼容] 从 JSON 字符串导出（须已为 camelCase 对象格式 {"pptId":N,"slides":[...]}） */
    String exportFromJson(String json, Long templateId, String fileName);

    /** [跨端兼容] 从已解析好的 DTO 导出 */
    String exportFromStructure(PptStructureDTO structure, Long templateId, String fileName);

    /** 只生成 PPTX 字节数组（不上传不入库，用于预览/批量打包） */
    byte[] generatePptxBytes(PptStructureDTO structure, Long templateId);

    /** 获取临时文件路径 */
    String getTempFilePath(Long prepId);
}
