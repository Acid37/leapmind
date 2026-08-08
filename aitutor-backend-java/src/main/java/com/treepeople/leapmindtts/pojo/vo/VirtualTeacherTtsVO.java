package com.treepeople.leapmindtts.pojo.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class VirtualTeacherTtsVO {
    private String audioUrl;
    private String contentType;
    private Long audioSize;
    private boolean cacheHit;
    private String cacheKey;
}
