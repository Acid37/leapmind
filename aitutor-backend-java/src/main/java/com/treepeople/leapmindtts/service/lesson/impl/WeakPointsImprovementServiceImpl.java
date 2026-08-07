package com.treepeople.leapmindtts.service.lesson.impl;

import com.treepeople.leapmindtts.mapper.UserExerciseMapper;
import com.treepeople.leapmindtts.mapper.UserWeakPointMapper;
import com.treepeople.leapmindtts.pojo.entity.UserExercise;
import com.treepeople.leapmindtts.pojo.entity.UserWeakPoint;
import com.treepeople.leapmindtts.pojo.vo.WeakPointsImprovementVO;
import com.treepeople.leapmindtts.service.lesson.WeakPointsImprovementService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 薄弱点改善追踪服务实现（M3 薄弱点模块）
 * <p>
 * 计算逻辑：
 * <ol>
 *   <li>取本期（最近 N 天）与上期（前 N 天）的练习记录（user_exercises）</li>
 *   <li>按知识点分别聚合正确率，比较两期差值</li>
 *   <li>差值 ≥ 阈值 → improved；差值 ≤ -阈值 → worsened</li>
 *   <li>本期未练习但状态已为 RESOLVED 的知识点也记为进步</li>
 *   <li>整体改善度 = 所有知识点正确率差值的平均值</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WeakPointsImprovementServiceImpl implements WeakPointsImprovementService {

    private final UserWeakPointMapper userWeakPointMapper;
    private final UserExerciseMapper userExerciseMapper;

    /** 判定"明显进步/退步"的正确率变化阈值（百分点） */
    private static final double CHANGE_THRESHOLD = 10.0;

    @Override
    public WeakPointsImprovementVO getImprovementReport(Long userId, String period) {
        int days = "week".equalsIgnoreCase(period) ? 7 : 30;

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime currentStart = now.minusDays(days);
        LocalDateTime previousStart = now.minusDays((long) days * 2);

        // 1. 两个时间窗口的练习记录
        List<UserExercise> currentExercises = userExerciseMapper.selectByTimeRange(userId, currentStart, now);
        List<UserExercise> previousExercises = userExerciseMapper.selectByTimeRange(userId, previousStart, currentStart);

        Map<String, double[]> currentAcc = aggregateAccuracy(currentExercises); // 知识点 -> {正确数, 总数}
        Map<String, double[]> previousAcc = aggregateAccuracy(previousExercises);

        // 2. 当前薄弱点状态
        List<UserWeakPoint> weakPoints = userWeakPointMapper.selectByUserId(userId);
        if (weakPoints == null) {
            weakPoints = Collections.emptyList();
        }

        // 3. 逐知识点对比两期正确率
        List<WeakPointsImprovementVO.KpChange> improved = new ArrayList<>();
        List<WeakPointsImprovementVO.KpChange> worsened = new ArrayList<>();

        Set<String> kpSet = new LinkedHashSet<>();
        kpSet.addAll(currentAcc.keySet());
        kpSet.addAll(previousAcc.keySet());

        double totalDelta = 0;
        int compared = 0;

        for (String kp : kpSet) {
            double before = accuracyOf(previousAcc, kp);
            double after = accuracyOf(currentAcc, kp);
            double delta = after - before;
            totalDelta += delta;
            compared++;

            if (delta >= CHANGE_THRESHOLD) {
                improved.add(WeakPointsImprovementVO.KpChange.builder()
                        .knowledgePoint(kp).before(before).after(after).build());
            } else if (delta <= -CHANGE_THRESHOLD) {
                worsened.add(WeakPointsImprovementVO.KpChange.builder()
                        .knowledgePoint(kp).before(before).after(after).build());
            }
        }

        // 4. 本期未练习但已判定"已解决"的知识点，也记为进步
        for (UserWeakPoint wp : weakPoints) {
            if ("RESOLVED".equalsIgnoreCase(wp.getStatus()) && !kpSet.contains(wp.getKnowledgePoint())) {
                double before = wp.getAccuracyRate() != null ? wp.getAccuracyRate().doubleValue() : 0.0;
                improved.add(WeakPointsImprovementVO.KpChange.builder()
                        .knowledgePoint(wp.getKnowledgePoint())
                        .before(before)
                        .after(100.0)
                        .build());
            }
        }

        // 5. 统计薄弱点分布
        int highCount = 0;
        int resolvedCount = 0;
        for (UserWeakPoint wp : weakPoints) {
            if ("HIGH".equalsIgnoreCase(wp.getWeaknessLevel())) {
                highCount++;
            }
            if ("RESOLVED".equalsIgnoreCase(wp.getStatus())) {
                resolvedCount++;
            }
        }

        double overall = compared == 0 ? 0.0 : totalDelta / compared;
        overall = Math.round(overall * 100.0) / 100.0;

        // 6. 学习建议：优先取已有 AI 建议，否则用兜底文案
        String suggestion = weakPoints.stream()
                .map(UserWeakPoint::getAiSuggestion)
                .filter(Objects::nonNull)
                .filter(s -> !s.isEmpty())
                .findFirst()
                .orElse("建议针对薄弱知识点保持每日专项练习，定期查看改善报告检验进步。");

        return WeakPointsImprovementVO.builder()
                .period(periodLabel(currentStart))
                .overallImprovement(overall)
                .improvedKps(improved)
                .worsenedKps(worsened)
                .totalWeakPoints(weakPoints.size())
                .highLevelCount(highCount)
                .resolvedCount(resolvedCount)
                .suggestion(suggestion)
                .build();
    }

    /**
     * 按知识点聚合练习记录：{知识点 -> [正确数, 总数]}
     */
    private Map<String, double[]> aggregateAccuracy(List<UserExercise> exercises) {
        Map<String, double[]> acc = new LinkedHashMap<>();
        if (exercises == null) {
            return acc;
        }
        for (UserExercise e : exercises) {
            if (e.getKnowledgePoint() == null) {
                continue;
            }
            double[] pair = acc.computeIfAbsent(e.getKnowledgePoint(), k -> new double[2]);
            pair[1]++; // 总数
            if (e.getIsCorrect() != null && e.getIsCorrect() == 1) {
                pair[0]++; // 正确数
            }
        }
        return acc;
    }

    /**
     * 计算某知识点正确率（%），保留两位小数；无数据返回 0
     */
    private double accuracyOf(Map<String, double[]> acc, String kp) {
        double[] pair = acc.get(kp);
        if (pair == null || pair[1] == 0) {
            return 0.0;
        }
        return Math.round(pair[0] * 10000.0 / pair[1]) / 100.0;
    }

    /**
     * 统计周期标签，如 2026-07
     */
    private String periodLabel(LocalDateTime start) {
        LocalDate d = start.toLocalDate();
        return d.getYear() + "-" + String.format("%02d", d.getMonthValue());
    }
}
