package com.treepeople.leapmindtts.pojo.dto;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * 批量语音合成请求对象
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BulkSynthesisRequest {

    @NotBlank(message = "课程ID不能为空")
    private String courseId;

    @NotBlank(message = "PPT标题不能为空")
    private String title;

    @NotEmpty(message = "slides不能为空")
    private List<SlideData> slides;

    private BulkSynthesisOptions options;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SlideData {

        @NotNull(message = "页码不能为空")
        @JsonProperty("page_number")
        private Integer pageNumber;

        @NotBlank(message = "页面标题不能为空")
        private String title;

        @NotEmpty(message = "内容点不能为空")
        @JsonProperty("content_points")
        private List<String> contentPoints;

        @JsonProperty("slide_type")
        private String slideType;

        private String type;

        private String description;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BulkSynthesisOptions {

        @Builder.Default
        @JsonProperty("enablePolishing")
        private Boolean enablePolishing = true;

        @Builder.Default
        @JsonProperty("audioFormat")
        private String audioFormat = "wav";

        @Builder.Default
        @JsonProperty("sampleRate")
        private Integer sampleRate = 16000;

        @Builder.Default
        @JsonProperty("saveOriginalText")
        private Boolean saveOriginalText = true;
    }
}
