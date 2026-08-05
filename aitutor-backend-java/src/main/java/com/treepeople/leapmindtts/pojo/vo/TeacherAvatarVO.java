package com.treepeople.leapmindtts.pojo.vo;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class TeacherAvatarVO {
    private String id;
    private String name;
    private String description;
    private String modelUrl;
    private String thumbnailUrl;
    private String voiceType;
    private String accent;
    private Boolean enabled;
    private Integer sortOrder;
    private BigDecimal speed;
}
