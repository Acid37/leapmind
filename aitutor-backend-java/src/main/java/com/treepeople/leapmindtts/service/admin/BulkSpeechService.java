package com.treepeople.leapmindtts.service.admin;

import com.treepeople.leapmindtts.pojo.dto.*;
import com.treepeople.leapmindtts.pojo.entity.LessonSession;
import jakarta.validation.constraints.NotBlank;

import java.util.List;

/**
 * 批量语音合成服务接口
 */
public interface BulkSpeechService {

    /**
     * 处理批量语音合成请求
     *
     * @param request 批量合成请求
     * @return 合成结果响应
     */
    BulkSynthesisResponse processBulkSynthesis(BulkSynthesisRequest request);

    /**
     * 获取指定页面的所有音频片段
     *
     * @param courseId 会话ID
     * @param pageNumber 页码
     * @return 音频片段列表
     */
    //1
    List<PPTAudioSegment> getPageAudioSegments(String courseId, Integer pageNumber);

    /**
     * 获取指定音频片段的详细信息
     *
     * @param courseId 会话ID
     * @param segmentIndex 片段索引
     * @return 音频片段信息
     */
    PPTAudioSegment getAudioSegment(String courseId, Integer segmentIndex);

    /**
     * 获取PPT的完整音频信息和统计数据
     *
     * @param courseId 会话ID
     * @return PPT音频信息
     */
    PPTAudioInfo getPPTAudioInfo(String courseId);

    /**
     * 批量文本预处理（不进行语音合成）
     *
     * @param request 批量合成请求
     * @return 预处理结果响应
     */
    BulkPreprocessingResponse processBulkPreprocessing(BulkSynthesisRequest request);

    /**
     * 执行批量语音合成（基于已预处理的文本）
     *
     * @param courseId 会话ID
     * @return 合成结果响应
     */
    BulkSynthesisResponse executeBulkSynthesis(String courseId);

    /**
     * 获取待审核的会话列表
     *
     * @return 待审核会话列表
     */
    List<LessonSession> getPendingReviewSessions();

    /**
     * 根据状态获取会话列表
     *
     * @param status 状态过滤条件，为空则返回所有会话
     * @return 会话列表
     */
    List<LessonSession> getSessionsByStatus(String status);

    /**
     * 审核会话
     *
     * @param courseId 会话ID
     * @param reviewerId 审核人ID
     * @param approved 是否通过
     * @param comments 审核意见
     * @return 审核结果
     */
    ReviewResponse reviewSession(String courseId, String reviewerId, Boolean approved, String comments);

    /**
     * 管理员审核会话（支持修改润色文本和其他属性）
     *
     * @param courseId 会话ID
     * @param request 审核请求（包含可修改的属性）
     * @return 审核结果
     */
    ReviewResponse adminReviewSession(String courseId, AdminReviewRequest request);


    LessonSession getPendingReviewSessionsByCourseId(@NotBlank String courseId);
}
