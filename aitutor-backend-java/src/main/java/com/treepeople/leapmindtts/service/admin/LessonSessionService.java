package com.treepeople.leapmindtts.service.admin;

import com.baomidou.mybatisplus.extension.service.IService;
import com.treepeople.leapmindtts.pojo.entity.LessonSession;

import java.util.List;

/**
 * 讲课会话服务接口
 */
public interface LessonSessionService extends IService<LessonSession> {

    /**
     * 根据会话ID查询会话信息
     */
    LessonSession getByCourseId(String courseId);

    /**
     * 创建新的讲课会话
     */
    boolean createSession(String courseId, String title, String originalText, String polishedText);

    /**
     * 更新会话的总片段数和总时长
     */
    boolean updateSessionStats(String courseId, Integer totalSegments, Long totalDuration);

    /**
     * 根据创建时间范围查询会话列表
     */
    List<LessonSession> getSessionsByTimeRange(String startTime, String endTime);

    /**
     * 根据标题关键词搜索会话
     */
    List<LessonSession> searchSessionsByTitle(String keyword);

    /**
     * 删除会话及其相关数据
     */
    boolean deleteSessionCompletely(String courseId);

    /**
     * 检查会话是否存在
     */
    boolean sessionExists(String courseId);

    /**
     * 更新会话的润色文本
     */
    boolean updatePolishedText(String courseId, String polishedText);

    /**
     * 更新会话标题
     */
    boolean updateTitle(String courseId, String title);

    /**
     * 根据状态查询会话列表
     */
    List<LessonSession> getSessionsByStatus(String status);

    /**
     * 更新会话状态
     */
    boolean updateSessionStatus(String courseId, String status, String reviewerId,
                              java.time.LocalDateTime reviewedAt, String comments);

    String getSessionStatus(String courseId);
}
