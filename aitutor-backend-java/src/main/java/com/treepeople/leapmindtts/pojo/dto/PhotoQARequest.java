package com.treepeople.leapmindtts.pojo.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 拍照答疑请求
 */
@Data
public class PhotoQARequest {

    @NotNull(message = "用户ID不能为空")
    private Long userId;

    private Long ocrRecordId;

    /** OCR识别后的题目结构化信息 */
    @NotNull(message = "题目信息不能为空")
    @Valid
    private QuestionInfo question;

    /** 用户画像（可选，从 M6 获取） */
    private UserProfileInfo userProfile;

    @Data
    public static class QuestionInfo {
        @NotBlank(message = "题目题干不能为空")
        private String stem;
        private List<String> options;
        private String subject;
        private String type;
    }

    @Data
    public static class UserProfileInfo {
        private String grade;
        private List<Map<String, Object>> weakPoints;
    }
}
