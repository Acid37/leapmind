package com.treepeople.leapmindtts.service.admin.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.treepeople.leapmindtts.mapper.LessonSessionMapper;
import com.treepeople.leapmindtts.pojo.entity.LessonSession;
import com.treepeople.leapmindtts.service.admin.LessonSessionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 讲课会话服务实现类
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LessonSessionServiceImpl extends ServiceImpl<LessonSessionMapper, LessonSession> implements LessonSessionService {

    private final LessonSessionMapper lessonSessionMapper;

    @Override
    public LessonSession getByCourseId(String courseId) {
        try {
            LessonSession session = lessonSessionMapper.selectByCourseId(courseId);
            if (session != null) {
                log.debug("查询会话成功，会话ID: {}", courseId);
            } else {
                log.warn("未找到会话，会话ID: {}", courseId);
            }
            return session;
        } catch (Exception e) {
            log.error("查询会话失败，会话ID: {}", courseId, e);
            return null;
        }
    }

    @Override
    public boolean createSession(String courseId, String title, String originalText, String polishedText) {
        try {
            LessonSession session = LessonSession.builder()
                    .courseId(courseId)
                    .title(title)
                    .originalText(originalText)
                    .polishedText(polishedText)
                    .totalSegments(0)
                    .totalDuration(0L)
                    .processingStatus("DRAFT") // 默认状态
                    .createdAt(LocalDateTime.now())
                    .build();

            boolean result = save(session);
            if (result) {
                log.info("创建会话成功，会话ID: {}, 标题: {}", courseId, title);
            } else {
                log.error("创建会话失败，会话ID: {}", courseId);
            }
            return result;
        } catch (Exception e) {
            log.error("创建会话异常，会话ID: {}", courseId, e);
            return false;
        }
    }

    @Override
    public boolean updateSessionStats(String courseId, Integer totalSegments, Long totalDuration) {
        try {
            int result = lessonSessionMapper.updateSessionStats(courseId, totalSegments, totalDuration);
            if (result > 0) {
                log.info("更新会话统计成功，会话ID: {}, 总片段数: {}, 总时长: {}ms",
                        courseId, totalSegments, totalDuration);
            } else {
                log.warn("更新会话统计失败，会话ID: {}", courseId);
            }
            return result > 0;
        } catch (Exception e) {
            log.error("更新会话统计异常，会话ID: {}", courseId, e);
            return false;
        }
    }

    @Override
    public List<LessonSession> getSessionsByTimeRange(String startTime, String endTime) {
        try {
            List<LessonSession> sessions = lessonSessionMapper.selectByTimeRange(startTime, endTime);
            log.info("查询时间范围内的会话，开始时间: {}, 结束时间: {}, 找到 {} 个会话",
                    startTime, endTime, sessions.size());
            return sessions;
        } catch (Exception e) {
            log.error("查询时间范围内的会话失败，开始时间: {}, 结束时间: {}", startTime, endTime, e);
            return List.of();
        }
    }

    @Override
    public List<LessonSession> searchSessionsByTitle(String keyword) {
        try {
            List<LessonSession> sessions = lessonSessionMapper.selectByTitleKeyword(keyword);
            log.info("根据标题关键词搜索会话，关键词: {}, 找到 {} 个会话", keyword, sessions.size());
            return sessions;
        } catch (Exception e) {
            log.error("根据标题关键词搜索会话失败，关键词: {}", keyword, e);
            return List.of();
        }
    }

    @Override
    public boolean deleteSessionCompletely(String courseId) {
        try {
            // 这里应该级联删除相关的音频片段等数据
            // 暂时只删除会话本身
            boolean result = removeById(courseId);
            if (result) {
                log.info("删除会话成功，会话ID: {}", courseId);
            } else {
                log.warn("删除会话失败，会话ID: {}", courseId);
            }
            return result;
        } catch (Exception e) {
            log.error("删除会话异常，会话ID: {}", courseId, e);
            return false;
        }
    }

    @Override
    public boolean sessionExists(String courseId) {
        try {
            LessonSession session = getByCourseId(courseId);
            return session != null;
        } catch (Exception e) {
            log.error("检查会话是否存在异常，会话ID: {}", courseId, e);
            return false;
        }
    }

    @Override
    public boolean updatePolishedText(String courseId, String polishedText) {
        try {
            int result = lessonSessionMapper.updatePolishedText(courseId, polishedText);
            if (result > 0) {
                log.info("更新会话润色文本成功，会话ID: {}, 文本长度: {}",
                        courseId, polishedText != null ? polishedText.length() : 0);
            } else {
                log.warn("更新会话润色文本失败，会话ID: {}", courseId);
            }
            return result > 0;
        } catch (Exception e) {
            log.error("更新会话润色文本异常，会话ID: {}", courseId, e);
            return false;
        }
    }

    @Override
    public boolean updateTitle(String courseId, String title) {
        try {
            int result = lessonSessionMapper.updateTitle(courseId, title);
            if (result > 0) {
                log.info("更新会话标题成功，会话ID: {}, 标题: {}", courseId, title);
            } else {
                log.warn("更新会话标题失败，会话ID: {}", courseId);
            }
            return result > 0;
        } catch (Exception e) {
            log.error("更新会话标题异常，会话ID: {}", courseId, e);
            return false;
        }
    }

    @Override
    public List<LessonSession> getSessionsByStatus(String status) {
        try {
            List<LessonSession> sessions = lessonSessionMapper.selectByStatus(status);
            log.info("查询指定状态的会话，状态: {}, 找到 {} 个会话", status, sessions.size());
            return sessions;
        } catch (Exception e) {
            log.error("查询指定状态的会话失败，状态: {}", status, e);
            return List.of();
        }
    }

    @Override
    public boolean updateSessionStatus(String courseId, String status, String reviewerId,
                                     LocalDateTime reviewedAt, String comments) {
        try {
            int result = lessonSessionMapper.updateSessionStatus(courseId, status, reviewerId, reviewedAt, comments);
            if (result > 0) {
                log.info("更新会话状态成功，会话ID: {}, 新状态: {}, 审核人: {}", courseId, status, reviewerId);
            } else {
                log.warn("更新会话状态失败，会话ID: {}, 状态: {}", courseId, status);
            }
            return result > 0;
        } catch (Exception e) {
            log.error("更新会话状态异常，会话ID: {}, 状态: {}", courseId, status, e);
            return false;
        }
    }

    @Override
    public String getSessionStatus(String courseId) {
        LessonSession lessonSession = getByCourseId(courseId);
        if (lessonSession != null) {
            return lessonSession.getProcessingStatus();
        }
        return null;
    }
}
