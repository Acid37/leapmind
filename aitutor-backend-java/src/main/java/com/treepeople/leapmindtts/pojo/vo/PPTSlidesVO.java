package com.treepeople.leapmindtts.pojo.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @ Package：com.treepeople.leapmindtts.pojo.vo
 * @ Project：leapMind-java
 * @ Description:
 * @ Date：2025/11/11  15:58
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PPTSlidesVO {

    private String courseId;

    private String title;

    private String htmlContent;
}
