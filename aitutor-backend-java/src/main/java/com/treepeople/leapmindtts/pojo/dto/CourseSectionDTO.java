package com.treepeople.leapmindtts.pojo.dto;

import com.treepeople.leapmindtts.pojo.enums.SemesterEnum;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

/**
 * @ Package：com.treepeople.leapmindtts.pojo.vo
 * @ Project：leapmind-tts - 语音分段
 * @ Description:
 * @ Date：2025/11/2  20:48
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class CourseSectionDTO {

    private String subject;
    // 阶段
    private String stageName;
    // 年级
    private String gradeName;
    // 学期
    private SemesterEnum semester;
    // 章
    private String chapterNumber;

}
