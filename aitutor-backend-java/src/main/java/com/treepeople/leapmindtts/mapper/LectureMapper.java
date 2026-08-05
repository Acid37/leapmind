package com.treepeople.leapmindtts.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.treepeople.leapmindtts.pojo.entity.Lecture;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * M4 讲课 Mapper（许沣睿）
 */
@Mapper
public interface LectureMapper extends BaseMapper<Lecture> {

    @Select("SELECT * FROM lectures WHERE course_id = #{courseId}")
    Lecture selectByCourseId(@Param("courseId") String courseId);

    @Select("SELECT * FROM lectures WHERE status = #{status} ORDER BY created_at DESC")
    List<Lecture> selectByStatus(@Param("status") String status);

    @Update("UPDATE lectures SET current_page = #{currentPage}, progress_ms = #{progressMs}, status = #{status}, updated_at = NOW() WHERE course_id = #{courseId}")
    int updateProgress(@Param("courseId") String courseId,
                       @Param("currentPage") Integer currentPage,
                       @Param("progressMs") Long progressMs,
                       @Param("status") String status);

    @Update("UPDATE lectures SET ppt_json_path = #{pptJsonPath}, generated_content = #{generatedContent}, total_pages = #{totalPages}, total_duration_ms = #{totalDurationMs}, status = 'READY', updated_at = NOW() WHERE course_id = #{courseId}")
    int updateGeneratedContent(@Param("courseId") String courseId,
                               @Param("pptJsonPath") String pptJsonPath,
                               @Param("generatedContent") String generatedContent,
                               @Param("totalPages") Integer totalPages,
                               @Param("totalDurationMs") Long totalDurationMs);

    @Update("UPDATE lectures SET playback_snapshot = #{snapshotJson}, updated_at = NOW() WHERE course_id = #{courseId}")
    int updatePlaybackSnapshot(@Param("courseId") String courseId,
                               @Param("snapshotJson") String snapshotJson);
}
