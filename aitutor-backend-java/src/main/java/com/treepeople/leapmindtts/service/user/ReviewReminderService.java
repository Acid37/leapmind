package com.treepeople.leapmindtts.service.user;

import com.treepeople.leapmindtts.pojo.dto.MarkReviewedRequest;
import com.treepeople.leapmindtts.pojo.vo.ReviewReminderVO;

import java.util.List;

/**
 * 复习提醒服务接口
 * <p>
 * 基于艾宾浩斯遗忘曲线理论，为用户提供智能复习提醒管理。
 * 复习提醒数据由 Python AI 服务定期计算生成，本服务负责：
 * <ul>
 *   <li>查询用户当前待复习的提醒列表</li>
 *   <li>处理用户标记已复习的操作</li>
 *   <li>提供复习历史查询</li>
 * </ul>
 *
 * @author wuminxi
 * @date 2026-07-21
 */
public interface ReviewReminderService {

    /**
     * 获取用户待复习提醒列表
     * <p>
     * 查询当前日期及之前所有未复习的提醒，按日期升序、优先级降序排列。
     * 包含逾期未复习的记录（scheduled_date < today），确保用户不会遗漏。
     *
     * @param userId 用户ID
     * @return 待复习提醒列表，空列表表示当前无待复习任务
     */
    List<ReviewReminderVO> getReviewReminders(Long userId);

    /**
     * 标记指定提醒为已复习
     * <p>
     * 业务规则：
     * <ol>
     *   <li>校验提醒是否存在，不存在则抛出 UserNotFoundException</li>
     *   <li>校验提醒是否属于当前用户，防止越权操作</li>
     *   <li>更新 is_reviewed=1, reviewed_at=当前时间</li>
     * </ol>
     *
     * @param userId  用户ID（用于权限校验）
     * @param request 标记已复习请求，包含提醒ID
     * @return 更新后的复习提醒视图对象
     * @throws com.treepeople.leapmindtts.exception.UserNotFoundException 提醒不存在时抛出
     * @throws IllegalArgumentException 提醒不属于当前用户时抛出
     */
    ReviewReminderVO markAsReviewed(Long userId, MarkReviewedRequest request);

    /**
     * 获取用户所有复习提醒（包含已复习和未复习）
     * <p>未复习的排在前面，已复习的排在后面，方便前端展示</p>
     *
     * @param userId 用户ID
     * @return 复习提醒完整列表
     */
    List<ReviewReminderVO> getAllReminders(Long userId);
}
