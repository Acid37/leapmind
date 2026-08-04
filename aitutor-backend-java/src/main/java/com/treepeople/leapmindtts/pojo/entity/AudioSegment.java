package com.treepeople.leapmindtts.pojo.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

/**
 * 音频片段表实体类（页面级存储）
 * 每条记录存储一页PPT的所有音频片段合并数据
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("audio_segments")
public class AudioSegment {
    
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    
    @TableField("course_id")
    private String courseId;
    
    @TableField("segment_index")
    private Integer segmentIndex; // 页面编号（页面级存储）
    
    @TableField("text_content")
    private String textContent; // 页面标题或描述
    
    @TableField("audio_data")
    private byte[] audioData; // 页面所有音频片段的合并数据
    
    @TableField("audio_size")
    private Long audioSize; // 页面总音频文件大小(字节)
    
    @TableField("duration")
    private Long duration; // 页面总音频时长(毫秒)
    
    @TableField("audio_format")
    private String audioFormat;
    
    @TableField("sample_rate")
    private Integer sampleRate;
    
    @TableField("checksum")
    private String checksum;
    
    // PPT上下文相关字段
    @TableField("slide_page_number")
    private Integer slidePageNumber;
    
    @TableField("slide_title")
    private String slideTitle;
    
    @TableField("slide_type")
    private String slideType;
    
    @TableField("slide_description")
    private String slideDescription;
    
    @TableField("original_text")
    private String originalText;
    
    @TableField("polished_text")
    private String polishedText;
    
    // 页面级存储相关字段
    @TableField("segment_count")
    private Integer segmentCount; // 该页面包含的音频片段总数
    
    @TableField("segment_metadata")
    private String segmentMetadata; // 片段元数据(JSON格式)
    
    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    
    // 新增：片段状态字段
    @TableField("segment_status")
    private String segmentStatus; // TEXT_ONLY, AUDIO_GENERATED
}