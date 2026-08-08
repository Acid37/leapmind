package com.treepeople.leapmindtts.pojo.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

/**
 * 知识点字典表实体类
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("knowledge_points")
public class KnowledgePoint {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("subject")
    private String subject;

    @TableField("grade")
    private String grade;

    @TableField("name")
    private String name;

    @TableField("parent_id")
    private Long parentId;

    @TableField("description")
    private String description;

    @TableField("level")
    private Integer level;
}
