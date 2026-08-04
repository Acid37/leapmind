package com.treepeople.leapmindtts.pojo.dto;

import com.treepeople.leapmindtts.pojo.enums.SemesterEnum;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 课程创建请求DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CourseCreateRequest {

    //@NotBlank(message = "课程ID不能为空")
    private String courseId;

    @NotBlank(message = "学科不能为空")
    private String subject;

    @NotBlank(message = "阶段名称不能为空")
    private String stageName;

    @NotBlank(message = "年级名称不能为空")
    private String gradeName;

    @NotNull(message = "学期不能为空")
    private SemesterEnum semester;

    @NotNull(message = "章节编号不能为空")
    private Integer chapterNumber;

    @NotBlank(message = "章节标题不能为空")
    private String chapterTitle;

    private String sectionContent;

    @NotNull(message = "小节编号不能为空")
    private Float sectionNumber;

    @NotBlank(message = "小节标题不能为空")
    private String sectionTitle;
}
