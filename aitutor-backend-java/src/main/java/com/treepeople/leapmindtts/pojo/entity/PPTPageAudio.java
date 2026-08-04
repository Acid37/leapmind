package com.treepeople.leapmindtts.pojo.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

/**
 * PPT页面音频表实体类
 * 每条记录存储一页PPT的所有音频片段（合并存储）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("ppt_page_audio")
public class PPTPageAudio {
    
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    
    @TableField("course_id")
    private String courseId;
    
    @TableField("page_number")
    private Integer pageNumber;
    
    @TableField("page_title")
    private String pageTitle;
    
    @TableField("slide_type")
    private String slideType;
    
    @TableField("slide_description")
    private String slideDescription;
    
    @TableField("segment_count")
    private Integer segmentCount;
    
    @TableField("merged_audio_data")
    private byte[] mergedAudioData;
    
    @TableField("total_audio_size")
    private Long totalAudioSize;
    
    @TableField("total_duration")
    private Long totalDuration;
    
    @TableField("audio_format")
    private String audioFormat;
    
    @TableField("sample_rate")
    private Integer sampleRate;
    
    @TableField("segment_metadata")
    private String segmentMetadata; // JSON格式存储每个片段的元数据
    
    @TableField("checksum")
    private String checksum;
    
    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    
    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}