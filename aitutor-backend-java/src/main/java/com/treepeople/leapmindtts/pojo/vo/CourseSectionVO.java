package com.treepeople.leapmindtts.pojo.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @ Package：com.treepeople.leapmindtts.pojo.vo
 * @ Project：leapmind-tts - 语音分段
 * @ Description:
 * @ Date：2025/11/2  20:48
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CourseSectionVO {

    private String courseId;

    private String subject;

    // 节编号
    private Float sectionNumber;

    private String sectionTitle;

    // 章编号
    private Integer chapterNumber;

    private String chapterTitle;

    private String sectionContent;
}
