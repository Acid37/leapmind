package com.treepeople.leapmindtts.pojo.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * M4 讲课创建请求
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LectureCreateRequest {
    @NotBlank(message = "courseId 不能为空")
    private String courseId;

    @NotBlank(message = "title 不能为空")
    private String title;
}
