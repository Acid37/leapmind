package com.treepeople.leapmindtts.pojo.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.Map;

/**
 * AI 内部调用请求（Java → Python）
 * JSON 字段对齐 Python AIGenerateRequest schema
 */
@Data
public class AICallRequest {

    /** 调用模块：explain/lesson/prep/qa */
    @NotBlank(message = "模块名不能为空")
    private String module;

    /** 场景类型：photo_qa/explain_wrong/generate_lesson */
    @NotBlank(message = "场景类型不能为空")
    private String scene;

    /** 结构化参数（传递给 Python PromptManager 构建 Prompt） */
    private Map<String, Object> params;

    /** 完整 Prompt（可选，模块/场景命中时由 Python PromptManager 自动构建） */
    private String prompt;

    /** 模型选择，默认从配置读取 */
    private String modelName;

    /** 最大输出 token */
    private Integer maxTokens;

    /** 温度参数 */
    private Double temperature;
}
