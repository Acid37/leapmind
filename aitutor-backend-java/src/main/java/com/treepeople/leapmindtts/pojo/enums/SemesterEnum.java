package com.treepeople.leapmindtts.pojo.enums;

import lombok.Getter;

/**
 * @ Package：com.treepeople.leapmindtts.pojo.enums
 * @ Project：leapmind-tts - 语音分段
 * @ Description:
 * @ Date：2025/11/2  17:10
 */
@Getter
public enum SemesterEnum {
    SEMESTER_1("上册"),
    SEMESTER_2("下册");

    private final String chineseName;

    SemesterEnum(String chineseName) {
        this.chineseName = chineseName;
    }

    /**
     * 根据中文名称获取枚举值
     */
    public static SemesterEnum fromChineseName(String chineseName) {
        for (SemesterEnum semester : values()) {
            if (semester.chineseName.equals(chineseName)) {
                return semester;
            }
        }
        throw new IllegalArgumentException("未知的学期名称: " + chineseName);
    }
}
