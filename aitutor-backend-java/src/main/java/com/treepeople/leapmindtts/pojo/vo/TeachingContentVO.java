package com.treepeople.leapmindtts.pojo.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 备课内容视图对象
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TeachingContentVO {

    /**
     * 主键ID
     */
    private Long id;

    /**
     * 备课ID
     */
    private Long prepId;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 备课标题
     */
    private String title;

    /**
     * 备课状态（draft-草稿, published-已发布, archived-已归档）
     */
    private String status;

    /**
     * PPT结构JSON
     */
    private String pptStructure;

    /**
     * 应用的模板ID
     */
    private Long templateId;

    /**
     * PPT导出下载链接
     */
    private String pptDownloadUrl;

    /**
     * 完整备课生成内容（含大纲、PPT、讲稿）
     */
    private String generatedContentJson;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;

    // ===== M5→M4 接口契约字段 =====

    /**
     * 备课类型（ppt）
     */
    private String type;

    /**
     * 科目（math/chinese/...）
     */
    private String subject;

    /**
     * 年级（grade_8/...）
     */
    private String grade;

    /**
     * 幻灯片页数
     */
    private Integer slideCount;

    /**
     * 风格模板标识
     */
    private String styleTemplate;

    /**
     * 知识点列表 [{id, name}]
     */
    private List<Map<String, Object>> knowledgePoints;
}
