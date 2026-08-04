package com.treepeople.leapmindtts.pojo.dto;

import lombok.Data;

/**
 * @ Package：com.treepeople.leapmindtts.pojo.dto
 * @ Project：leapmind-tts - 语音分段
 * @ Description:
 * @ Date：2025/8/5  18:07
 */

@Data
public class SegmentedLessonConfig {
    /**
     * 最大片段长度
     */
    private Integer maxSegmentLength;

    /**
     * 是否启用预加载
     */
    private Boolean enablePreload;

    /**
     * 预加载片段数量
     */
    private Integer preloadSegments;
}
