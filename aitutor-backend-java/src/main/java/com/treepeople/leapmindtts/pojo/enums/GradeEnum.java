package com.treepeople.leapmindtts.pojo.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

/**
 * 年级枚举类
 * 支持从小学一年级到初三的年级分类
 */
@Getter
public enum GradeEnum {

    GRADE_1("GRADE_1", "小学一年级"),
    GRADE_2("GRADE_2", "小学二年级"),
    GRADE_3("GRADE_3", "小学三年级"),
    GRADE_4("GRADE_4", "小学四年级"),
    GRADE_5("GRADE_5", "小学五年级"),
    GRADE_6("GRADE_6", "小学六年级"),
    GRADE_7("GRADE_7", "初一"),
    GRADE_8("GRADE_8", "初二"),
    GRADE_9("GRADE_9", "初三"),
    GRADE_10("GRADE_10", "高一"),
    GRADE_11("GRADE_11", "高二"),
    GRADE_12("GRADE_12", "高三");

    @EnumValue
    @JsonValue
    private final String code;

    private final String description;

    GradeEnum(String code, String description) {
        this.code = code;
        this.description = description;
    }

    /**
     * 根据代码获取枚举
     *
     * @param code 年级代码
     * @return 对应的枚举值
     */
    public static GradeEnum fromCode(String code) {
        for (GradeEnum grade : values()) {
            if (grade.getCode().equals(code)) {
                return grade;
            }
        }
        throw new IllegalArgumentException("未知的年级代码: " + code);
    }

    /**
     * 获取教育阶段
     *
     * @return 教育阶段名称
     */
    public String getStage() {
        switch (this) {
            case GRADE_1:
            case GRADE_2:
            case GRADE_3:
            case GRADE_4:
            case GRADE_5:
            case GRADE_6:
                return "小学";
            case GRADE_7:
            case GRADE_8:
            case GRADE_9:
                return "初中";
            case GRADE_10:
            case GRADE_11:
            case GRADE_12:
                return "高中";
            default:
                return "未知";
        }
    }

    /**
     * 获取教育阶段代码
     *
     * @return 教育阶段代码
     */
    public String getStageCode() {
        switch (this) {
            case GRADE_1:
            case GRADE_2:
            case GRADE_3:
            case GRADE_4:
            case GRADE_5:
            case GRADE_6:
                return "PRIMARY";
            case GRADE_7:
            case GRADE_8:
            case GRADE_9:
                return "JUNIOR";
            case GRADE_10:
            case GRADE_11:
            case GRADE_12:
                return "SENIOR";
            default:
                return "UNKNOWN";

        }
    }
}