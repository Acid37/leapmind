package com.treepeople.leapmindtts.service.lesson;

import com.treepeople.leapmindtts.pojo.dto.PPTAudioSegment;
import com.treepeople.leapmindtts.pojo.entity.AudioSegment;

import java.util.List;

/**
 * 页面级音频服务接口
 * 使用现有的audio_segments表，但采用页面级存储策略
 */
public interface PageLevelAudioService {

    /**
     * 保存页面级音频数据
     *
     * @param courseId 会话ID
     * @param pageNumber 页码
     * @param pageTitle 页面标题
     * @param slideType 页面类型
     * @param slideDescription 页面描述
     * @param audioSegments 音频片段列表（包含音频数据和元数据）
     * @param audioFormat 音频格式
     * @param sampleRate 采样率
     * @return 是否保存成功
     */
    boolean savePageAudio(String courseId, Integer pageNumber, String pageTitle,
                         String slideType, String slideDescription,
                         List<PPTAudioSegment> audioSegments,
                         String audioFormat, Integer sampleRate);

    /**
     * 获取页面音频数据
     *
     * @param courseId 会话ID
     * @param pageNumber 页码
     * @return 页面音频数据
     */
    AudioSegment getPageAudio(String courseId, Integer pageNumber);

    /**
     * 获取页面的指定音频片段
     *
     * @param courseId 会话ID
     * @param pageNumber 页码
     * @param segmentIndex 片段索引（页面内的相对索引）
     * @return 音频片段数据
     */
    byte[] getPageAudioSegment(String courseId, Integer pageNumber, Integer segmentIndex);

    /**
     * 获取会话的所有页面音频
     *
     * @param courseId 会话ID
     * @return 页面音频列表
     */
    List<AudioSegment> getSessionPageAudios(String courseId);

    /**
     * 获取页面的所有音频片段信息（不包含音频数据）
     *
     * @param courseId 会话ID
     * @param pageNumber 页码
     * @return 音频片段信息列表
     */
    List<PPTAudioSegment> getPageAudioSegments(String courseId, Integer pageNumber);

    /**
     * 根据全局片段索引获取音频片段
     *
     * @param courseId 会话ID
     * @param globalSegmentIndex 全局片段索引
     * @return 音频片段数据
     */
    byte[] getAudioSegmentByGlobalIndex(String courseId, Integer globalSegmentIndex);

    /**
     * 根据全局片段索引获取音频片段信息
     *
     * @param courseId 会话ID
     * @param globalSegmentIndex 全局片段索引
     * @return 音频片段信息
     */
    PPTAudioSegment getAudioSegmentInfoByGlobalIndex(String courseId, Integer globalSegmentIndex);

    /**
     * 删除页面音频数据
     *
     * @param courseId 会话ID
     * @param pageNumber 页码
     * @return 是否删除成功
     */
    boolean deletePageAudio(String courseId, Integer pageNumber);

    /**
     * 删除会话的所有页面音频数据
     *
     * @param courseId 会话ID
     * @return 删除的记录数
     */
    int deleteSessionPageAudios(String courseId);

    /**
     * 获取会话的音频统计信息
     *
     * @param courseId 会话ID
     * @return 统计信息 [总音频大小, 总时长, 总片段数]
     */
    long[] getSessionAudioStats(String courseId);

    /**
     * 获取页面的完整音频数据
     *
     * @param courseId 会话ID
     * @param pageNumber 页码
     * @return 页面完整音频数据
     */
    byte[] getPageAudioData(String courseId, Integer pageNumber);

    /**
     * 获取页面的片段元数据信息（不包含音频数据）
     *
     * @param courseId 会话ID
     * @param pageNumber 页码
     * @return 片段元数据列表
     */
    List<PPTAudioSegment> getPageSegmentMetadata(String courseId, Integer pageNumber);

    // 新增：支持审核流程的方法

    /**
     * 保存仅包含文本的片段（不包含音频数据）
     *
     * @param courseId 会话ID
     * @param pageNumber 页码
     * @param pageTitle 页面标题
     * @param slideType 页面类型
     * @param slideDescription 页面描述
     * @param textSegments 文本片段列表
     * @return 是否保存成功
     */
    boolean saveTextOnlySegments(String courseId, Integer pageNumber, String pageTitle,
                               String slideType, String slideDescription,
                               List<PPTAudioSegment> textSegments);

    /**
     * 删除会话中所有仅包含文本的片段（TEXT_ONLY状态）
     *
     * @param courseId 会话ID
     * @return 是否删除成功
     */
    boolean deleteTextOnlySegments(String courseId);

    /**
     * 获取会话中所有仅包含文本的片段（TEXT_ONLY状态）
     *
     * @param courseId 会话ID
     * @return 文本片段列表
     */
    List<AudioSegment> getTextOnlySegments(String courseId);

    /**
     * 为文本片段更新音频数据
     *
     * @param courseId 会话ID
     * @param pageNumber 页码
     * @param audioSegments 包含音频数据的片段列表
     * @return 是否更新成功
     */
    boolean updateSegmentsWithAudio(String courseId, Integer pageNumber, List<PPTAudioSegment> audioSegments);

}
