package com.treepeople.leapmindtts.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.treepeople.leapmindtts.mapper.UserExerciseMapper;
import com.treepeople.leapmindtts.mapper.UserWeakPointMapper;
import com.treepeople.leapmindtts.pojo.entity.UserExercise;
import com.treepeople.leapmindtts.pojo.entity.UserWeakPoint;
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
 * 每天凌晨 02:30 全量重算薄弱点数据：
 * <ol>
 *   <li>基于 {@code user_exercises} 练习记录，重算每个薄弱点的答题数/错题数/正确率</li>
 *   <li>按正确率自动更新薄弱等级（weakness_level）与状态（status）</li>
 * </ol>
 * 与 {@code WeakPointsServiceImpl.recordExerciseResult} 的"事件驱动实时更新"互补，
 * 作为每日兜底，纠正因数据异常或漏记导致的偏差。
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class WeakPointCalculationTask {

    private final UserWeakPointMapper userWeakPointMapper;
    private final UserExerciseMapper userExerciseMapper;

    /** 正确率 >= 80% 视为已解决 */
    private static final BigDecimal MASTERED_THRESHOLD = new BigDecimal("80");
    /** 正确率 >= 60% 视为改善中 */
    private static final BigDecimal IMPROVING_THRESHOLD = new BigDecimal("60");

    /**
     * 全量重算：每天 02:30 执行
     */
    @Scheduled(cron = "0 30 2 * * ?")
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
     * 重算单个薄弱点：统计字段 + 等级 + 状态
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

        // 2. 重算统计字段
        wp.setTotalCount((int) total);
        wp.setErrorCount((int) errors);
        wp.setAccuracyRate(accuracy);

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
    }
}
