package com.treepeople.leapmindtts.pojo.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class VirtualTeacherTtsRequest {
    @Size(max = 64)
    private String courseId;
    @NotBlank
    @Size(max = 500)
    private String text;
    @Size(max = 100)
    private String voiceType;
    @DecimalMin("0.50")
    @DecimalMax("2.00")
    private Double speed = 1.0;
}
