package com.treepeople.leapmindtts.pojo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class TeacherAvatarRequest {
    @NotBlank
    @Pattern(regexp = "^[a-zA-Z0-9_-]{1,64}$")
    private String avatarCode;
    @NotBlank
    @Size(max = 100)
    private String name;
    @Size(max = 500)
    private String description;
    @NotBlank
    @Size(max = 500)
    @Pattern(regexp = "^(https?://|/).+")
    private String modelUrl;
    @Size(max = 500)
    @Pattern(regexp = "^(https?://|/).+")
    private String thumbnailUrl;
    @NotBlank
    @Size(max = 100)
    private String voiceType;
    @Size(max = 50)
    private String accent;
    private Boolean enabled = true;
    private Integer sortOrder = 0;
}
