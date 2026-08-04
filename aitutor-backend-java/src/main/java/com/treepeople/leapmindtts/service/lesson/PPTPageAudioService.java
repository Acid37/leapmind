package com.treepeople.leapmindtts.service.lesson;

import com.treepeople.leapmindtts.pojo.entity.PPTPageAudio;
import com.treepeople.leapmindtts.pojo.dto.PPTAudioSegment;

import java.util.List;

/**
 * PPT页面音频服务接口
 */
public interface PPTPageAudioService {

    /**
     * 保存页面音频数据
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
    PPTPageAudio getPageAudio(String courseId, Integer pageNumber);

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
    List<PPTPageAudio> getSessionPageAudios(String courseId);

    /**
     * 获取页面的所有音频片段信息（不包含音频数据）
     *
     * @param courseId 会话ID
     * @param pageNumber 页码
     * @return 音频片段信息列表
     */
    List<PPTAudioSegment> getPageAudioSegments(String courseId, Integer pageNumber);

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
}
