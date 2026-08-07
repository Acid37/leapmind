package com.treepeople.leapmindtts.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.treepeople.leapmindtts.pojo.entity.ReviewReminder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDate;
import java.util.List;

/**
 * 复习提醒数据访问层
 * <p>
 * 继承 MyBatis-Plus 的 BaseMapper，自动拥有通用 CRUD 方法。
 * 自定义 SQL 方法专注于复习提醒业务场景的查询与更新。
 * <p>
 * 查询规则说明：
 * <ul>
 *   <li>所有查询按 scheduled_date 升序、priority 降序排列</li>
 *   <li>pending 查询仅返回 scheduled_date <= 今天的记录（逾期也算待复习）</li>
 *   <li>markAsReviewed 使用 NOW() 函数由数据库生成时间戳</li>
 * </ul>
 *
 * @author wuminxi
 * @date 2026-07-21
 */
@Mapper
public interface ReviewReminderMapper extends BaseMapper<ReviewReminder> {

    /**
     * 根据用户ID和复习状态查询复习提醒列表
     * <p>可按 isReviewed 分别查询待复习(0)和已复习(1)记录</p>
     *
     * @param userId     用户ID
     * @param isReviewed 复习状态：0-未复习，1-已复习
     * @return 复习提醒列表，按日期升序、优先级降序排列
     */
    @Select("SELECT * FROM review_reminders WHERE user_id = #{userId} AND is_reviewed = #{isReviewed} ORDER BY scheduled_date ASC, priority DESC")
    List<ReviewReminder> selectByUserIdAndStatus(@Param("userId") Long userId, @Param("isReviewed") Integer isReviewed);

    /**
     * 根据用户ID查询所有到期待复习的提醒（含逾期未复习）
     * <p>这是"今日复习"页面的核心查询，返回 scheduled_date <= today 且未复习的所有记录</p>
     *
     * @param userId 用户ID
     * @param today  当前日期（用于对比 scheduled_date）
     * @return 待复习提醒列表，逾期记录也会被包含在内
     */
    @Select("SELECT * FROM review_reminders WHERE user_id = #{userId} AND is_reviewed = 0 AND scheduled_date <= #{today} ORDER BY scheduled_date ASC, priority DESC")
    List<ReviewReminder> selectPendingByUserId(@Param("userId") Long userId, @Param("today") LocalDate today);

    /**
     * 标记指定ID的提醒为已复习
     * <p>同时更新 reviewed_at 和 updated_at 为当前时间</p>
     *
     * @param id 复习提醒ID
     * @return 受影响行数，1 表示成功，0 表示记录不存在
     */
    @Update("UPDATE review_reminders SET is_reviewed = 1, reviewed_at = NOW(), updated_at = NOW() WHERE id = #{id}")
    int markAsReviewed(@Param("id") Long id);

    /**
     * 查询所有到期未复习的提醒（不区分用户）
     * <p>供定时任务使用，用于全量扫描和过期清理</p>
     *
     * @param today 当前日期
     * @return 所有用户待复习提醒列表
     */
    @Select("SELECT * FROM review_reminders WHERE is_reviewed = 0 AND scheduled_date <= #{today} ORDER BY user_id, scheduled_date ASC")
    List<ReviewReminder> selectAllPendingReminders(@Param("today") LocalDate today);
}
