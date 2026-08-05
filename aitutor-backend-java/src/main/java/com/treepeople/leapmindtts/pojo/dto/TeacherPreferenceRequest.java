package com.treepeople.leapmindtts.pojo.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class TeacherPreferenceRequest {
    @NotBlank
    @Size(max = 64)
    private String avatarId;
    @Size(max = 100)
    private String voiceType;
    @DecimalMin("0.50")
    @DecimalMax("2.00")
    private BigDecimal speed = BigDecimal.ONE;
}
