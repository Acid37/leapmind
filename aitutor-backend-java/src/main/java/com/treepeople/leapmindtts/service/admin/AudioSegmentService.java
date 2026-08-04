package com.treepeople.leapmindtts.service.admin;

import com.baomidou.mybatisplus.extension.service.IService;
import com.treepeople.leapmindtts.pojo.entity.AudioSegment;

import java.util.List;

/**
 * 音频片段服务接口
 */
public interface AudioSegmentService extends IService<AudioSegment> {

    /**
     * 根据会话ID和片段索引查询音频片段
     */
    AudioSegment getBySessionAndIndex(String courseId, Integer segmentIndex);

    /**
     * 根据会话ID查询所有音频片段
     */
    List<AudioSegment> getByCourseId(String courseId);

    /**
     * 根据会话ID查询从指定片段开始的所有音频片段
     */
    List<AudioSegment> getFromSegmentIndex(String courseId, Integer startIndex);

    /**
     * 保存音频片段
     */
    boolean saveAudioSegment(String courseId, Integer segmentIndex, String textContent,
                           byte[] audioData, String audioFormat, Integer sampleRate);

    /**
     * 批量保存音频片段
     */
    boolean saveBatchAudioSegments(List<AudioSegment> segments);

    /**
     * 删除指定会话的所有音频片段
     */
    boolean deleteByCourseId(String courseId);

    /**
     * 查询指定会话的音频片段数量
     */
    int countByCourseId(String courseId);

    /**
     * 查询指定会话的总音频大小
     */
    Long getTotalAudioSize(String courseId);

    /**
     * 查询指定会话的总时长
     */
    Long getTotalDuration(String courseId);

    /**
     * 检查音频片段是否存在
     */
    boolean segmentExists(String courseId, Integer segmentIndex);

    /**
     * 更新音频片段的校验和
     */
    boolean updateChecksum(String courseId, Integer segmentIndex, String checksum);

    // ========== PPT相关查询方法 ==========

    /**
     * 保存音频片段（支持AudioSegment对象）
     */
    boolean saveAudioSegment(AudioSegment segment);

    /**
     * 根据会话ID和页码查询音频片段
     */
    List<AudioSegment> getBySessionAndSlide(String courseId, Integer pageNumber);

    /**
     * 根据会话ID、页码和内容点索引查询音频片段
     */
    List<AudioSegment> getBySessionSlideAndContentPoint(String courseId, Integer pageNumber, Integer contentPointIndex);


    boolean isSessionExist(String courseId);


    boolean isAudioSynthesisExist(String courseId);
}
