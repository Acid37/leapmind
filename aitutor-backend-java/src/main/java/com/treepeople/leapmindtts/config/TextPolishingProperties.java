package com.treepeople.leapmindtts.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 文本润色配置属性类
 * 用于管理通义千问文本润色功能的所有配置参数
 */
@ConfigurationProperties(prefix = "text-polishing")
@Validated
public class TextPolishingProperties {

    /**
     * 是否启用文本润色功能
     */
    private boolean enabled = true;

    /**
     * API调用超时时间（秒）
     */
    @Min(value = 1, message = "超时时间不能小于1秒")
    @Max(value = 30, message = "超时时间不能超过30秒")
    private int timeout = 5;

    /**
     * 最大重试次数
     */
    @Min(value = 0, message = "重试次数不能小于0")
    @Max(value = 5, message = "重试次数不能超过5")
    private int maxRetries = 1;

    /**
     * 润色提示词模板
     */
    @NotBlank(message = "润色提示词不能为空")
    private String prompt = "请将以下文本润色为适合老师讲课的内容，要求：\n" +
            "1. 保持原意不变，确保信息准确性\n" +
            "2. 语言更加生动、易懂，适合学生理解\n" +
            "3. 适合口语化表达，增强互动感\n" +
            "4. 增加适当的过渡词和解释，提升连贯性\n" +
            "5. 保持专业术语的准确性，必要时添加简单解释\n" +
            "6. 【重要】润色后的内容字数不得超过原文字数+200字\n" +
            "7. 只返回润色后的教学内容，不要包含任何说明、引导语、分隔符或额外的话语\n\n" +
            "原文：";

    /**
     * 降级配置
     */
    @Valid
    @NotNull
    private Fallback fallback = new Fallback();

    /**
     * 验证配置
     */
    @Valid
    @NotNull
    private Validation validation = new Validation();

    // Getters and Setters
    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getTimeout() {
        return timeout;
    }

    public void setTimeout(int timeout) {
        this.timeout = timeout;
    }

    public int getMaxRetries() {
        return maxRetries;
    }

    public void setMaxRetries(int maxRetries) {
        this.maxRetries = maxRetries;
    }

    public String getPrompt() {
        return prompt;
    }

    public void setPrompt(String prompt) {
        this.prompt = prompt;
    }

    public Fallback getFallback() {
        return fallback;
    }

    public void setFallback(Fallback fallback) {
        this.fallback = fallback;
    }

    public Validation getValidation() {
        return validation;
    }

    public void setValidation(Validation validation) {
        this.validation = validation;
    }

    /**
     * 降级机制配置
     */
    public static class Fallback {
        /**
         * 是否启用降级机制
         */
        private boolean enabled = true;

        /**
         * 错误时是否使用原始文本
         */
        private boolean useOriginalOnError = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public boolean isUseOriginalOnError() {
            return useOriginalOnError;
        }

        public void setUseOriginalOnError(boolean useOriginalOnError) {
            this.useOriginalOnError = useOriginalOnError;
        }
    }

    /**
     * 文本验证配置
     */
    public static class Validation {
        /**
         * 最大文本长度
         */
        @Min(value = 1, message = "最大文本长度不能小于1")
        @Max(value = 10000, message = "最大文本长度不能超过10000")
        private int maxTextLength = 1000;

        /**
         * 最小文本长度
         */
        @Min(value = 1, message = "最小文本长度不能小于1")
        private int minTextLength = 5;

        public int getMaxTextLength() {
            return maxTextLength;
        }

        public void setMaxTextLength(int maxTextLength) {
            this.maxTextLength = maxTextLength;
        }

        public int getMinTextLength() {
            return minTextLength;
        }

        public void setMinTextLength(int minTextLength) {
            this.minTextLength = minTextLength;
        }

        /**
         * 润色后允许超出原文的最大字符数
         * 默认200，用于在服务端强制收敛生成长度，避免模型忽略提示词导致过长输出
         */
        @Min(value = 0, message = "maxExtraChars不能小于0")
        private int maxExtraChars = 200;

        public int getMaxExtraChars() {
            return maxExtraChars;
        }

        public void setMaxExtraChars(int maxExtraChars) {
            this.maxExtraChars = maxExtraChars;
        }
    }

    /**
     * 验证配置参数的有效性
     */
    public void validateConfiguration() {
        if (timeout <= 0) {
            throw new IllegalArgumentException("超时时间必须大于0");
        }
        
        if (maxRetries < 0) {
            throw new IllegalArgumentException("重试次数不能小于0");
        }
        
        if (prompt == null || prompt.trim().isEmpty()) {
            throw new IllegalArgumentException("润色提示词不能为空");
        }
        
        if (validation.minTextLength > validation.maxTextLength) {
            throw new IllegalArgumentException("最小文本长度不能大于最大文本长度");
        }
    }
}