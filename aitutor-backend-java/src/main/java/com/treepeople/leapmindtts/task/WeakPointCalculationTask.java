package com.treepeople.leapmindtts.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.treepeople.leapmindtts.mapper.UserExerciseMapper;
import com.treepeople.leapmindtts.mapper.UserWeakPointMapper;
import com.treepeople.leapmindtts.pojo.entity.UserExercise;
import com.treepeople.leapmindtts.pojo.entity.UserWeakPoint;
import com.treepeople.leapmindtts.service.lesson.EventCollectionClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * 薄弱点计算定时任务（M3 薄弱点模块）
 * <p>
 * ⚠️ 已废弃：全量重算已由 Python M3 计算引擎接管（{@code backend/calculator/scheduler.py}）。
 * Python 引擎使用三维加权公式（错误率 + 近期正确率 + 困惑频率）和多源数据
 * （user_answers / conversation_messages / wrong_question_book / user_profiles），
 * 比 Java 仅按正确率分档更准确。Java 侧仅保留"事件驱动实时更新"（
 * {@code WeakPointsServiceImpl.recordExerciseResult}），未来将改为调用 Python 增量接口。
 * <p>
 * 如需恢复此定时任务（如 Python 引擎不可用时作为降级兜底），取消下方两个注解即可。
 * <p>
 * 原逻辑：每天凌晨 02:30 全量重算薄弱点数据：
 * <ol>
 *   <li>基于 {@code user_exercises} 练习记录，重算每个薄弱点的答题数/错题数/正确率</li>
 *   <li>按正确率自动更新薄弱等级（weakness_level）与状态（status）</li>
 * </ol>
 */
// @Component  // 已由 Python M3 引擎接管，取消注释可恢复
@Slf4j
@RequiredArgsConstructor
public class WeakPointCalculationTask {

    private final UserWeakPointMapper userWeakPointMapper;
    private final UserExerciseMapper userExerciseMapper;
    private final EventCollectionClient eventCollectionClient;

    /** 正确率 >= 80% 视为已解决 */
    private static final BigDecimal MASTERED_THRESHOLD = new BigDecimal("80");
    /** 正确率 >= 60% 视为改善中 */
    private static final BigDecimal IMPROVING_THRESHOLD = new BigDecimal("60");

    /**
     * 全量重算：每天 02:30 执行
     * ⚠️ 已由 Python M3 引擎接管调度，取消注释可恢复
     */
    // @Scheduled(cron = "0 30 2 * * ?")  // 已由 Python M3 引擎接管
    public void executeFullWeakPointRecalculation() {
        log.info("========== 薄弱点全量重算定时任务开始 ==========");
        try {
            List<UserWeakPoint> all = userWeakPointMapper.selectList(null);
            if (all == null || all.isEmpty()) {
                log.info("暂无薄弱点数据，跳过重算");
                return;
            }

            int updated = 0;
            for (UserWeakPoint wp : all) {
                try {
                    recalculate(wp);
                    updated++;
                } catch (Exception e) {
                    log.error("重算薄弱点失败: id={}, knowledgePoint={}, error={}",
                            wp.getId(), wp.getKnowledgePoint(), e.getMessage());
                }
            }
            log.info("薄弱点全量重算完成，共处理 {} 条，更新 {} 条", all.size(), updated);
        } catch (Exception e) {
            log.error("薄弱点全量重算任务失败: {}", e.getMessage(), e);
        }
        log.info("========== 薄弱点全量重算定时任务完成 ==========");
    }

    /**
     * 重算单个薄弱点：统计字段 + 等级 + 状态，变化时上报事件
     */
    private void recalculate(UserWeakPoint wp) {
        // 1. 查询该用户该知识点的全部练习记录
        List<UserExercise> exercises = userExerciseMapper.selectList(
                new LambdaQueryWrapper<UserExercise>()
                        .eq(UserExercise::getUserId, wp.getUserId())
                        .eq(UserExercise::getKnowledgePoint, wp.getKnowledgePoint()));
        if (exercises == null || exercises.isEmpty()) {
            return;
        }

        long total = exercises.size();
        long correct = exercises.stream()
                .filter(e -> e.getIsCorrect() != null && e.getIsCorrect() == 1)
                .count();
        long errors = total - correct;
        BigDecimal accuracy = BigDecimal.valueOf(correct * 100.0 / total)
                .setScale(2, RoundingMode.HALF_UP);

        // 捕获旧分数（用于 M6 事件上报）
        BigDecimal oldScore = calculateWeaknessScore(wp);

        // 2. 重算统计字段（同时同步 Python 引擎字段）
        wp.setTotalCount((int) total);
        wp.setTotalAttempts((int) total);
        wp.setErrorCount((int) errors);
        wp.setAccuracyRate(accuracy);
        // error_rate 保持 Python 权威值不变（Java 不覆盖）

        // 3. 按正确率更新薄弱等级与状态
        if (accuracy.compareTo(MASTERED_THRESHOLD) >= 0) {
            wp.setWeaknessLevel("LOW");
            wp.setStatus("RESOLVED");
        } else if (accuracy.compareTo(IMPROVING_THRESHOLD) >= 0) {
            wp.setWeaknessLevel("MEDIUM");
            wp.setStatus("IMPROVING");
        } else {
            wp.setWeaknessLevel("HIGH");
            wp.setStatus("ACTIVE");
        }

        userWeakPointMapper.updateById(wp);

        // 4. 上报 weak_point_changed 事件（分数有变化时）
        BigDecimal newScore = calculateWeaknessScore(wp);
        if (oldScore.compareTo(newScore) != 0) {
            eventCollectionClient.reportWeakPointChanged(
                    wp.getUserId(),
                    wp.getKpId(),
                    oldScore,
                    newScore,
                    "RECALCULATED");
        }
    }

    /**
     * 计算薄弱度分数（0-1 范围，供 M6 画像引擎使用）
     * <p>
     * 优先使用 Python 引擎计算的权威 {@code weakness_score}；
     * 若 Python 尚未计算，回退到简易公式或等级估算。
     */
    private BigDecimal calculateWeaknessScore(UserWeakPoint wp) {
        // 优先：Python 引擎计算的权威分数
        if (wp.getWeaknessScore() != null) {
            return wp.getWeaknessScore();
        }
        // 回退1：用正确率反推
        if (wp.getAccuracyRate() != null
                && wp.getTotalCount() != null
                && wp.getTotalCount() > 0) {
            return BigDecimal.ONE.subtract(
                    wp.getAccuracyRate().divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP));
        }
        // 回退2：按薄弱等级估算
        String level = wp.getWeaknessLevel() != null ? wp.getWeaknessLevel().toUpperCase() : "MEDIUM";
        switch (level) {
            case "HIGH":   return BigDecimal.valueOf(0.80);
            case "MEDIUM": return BigDecimal.valueOf(0.50);
            case "LOW":    return BigDecimal.valueOf(0.30);
            default:       return BigDecimal.valueOf(0.50);
        }
    }
}
