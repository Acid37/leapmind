package com.treepeople.leapmindtts.pojo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @ Package：com.treepeople.leapmindtts.pojo.entity
 * @ Project：leapmind-tts - 语音分段
 * @ Description:
 * @ Date：2025/11/2  20:40
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@TableName("course_schedule")
public class CourseSection {

    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    @TableField("course_id")
    private String courseId;

    @TableField("subject")
    private String subject;

    @TableField("stage_name")
    private String stageName;

    @TableField("grade_name")
    private String gradeName;

    @TableField("semester")
    private String semester;

    @TableField("chapter_number")
    private Integer chapterNumber;

    @TableField("chapter_title")
    private String chapterTitle;

    @TableField("section_content")
    private String sectionContent;

    @TableField("section_number")
    private Float sectionNumber;

    @TableField("section_title")
    private String sectionTitle;





}
