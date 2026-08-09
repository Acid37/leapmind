package com.treepeople.leapmindtts.service.lesson.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.treepeople.leapmindtts.mapper.KnowledgePointMapper;
import com.treepeople.leapmindtts.mapper.UserExerciseMapper;
import com.treepeople.leapmindtts.mapper.UserWeakPointMapper;
import com.treepeople.leapmindtts.pojo.dto.AiAnalysisRequest;
import com.treepeople.leapmindtts.pojo.dto.AiAnalysisResponse;
import com.treepeople.leapmindtts.pojo.dto.ExerciseRecordRequest;
import com.treepeople.leapmindtts.pojo.dto.PracticePlanRequest;
import com.treepeople.leapmindtts.pojo.entity.UserExercise;
import com.treepeople.leapmindtts.pojo.entity.UserWeakPoint;
import com.treepeople.leapmindtts.pojo.result.PageResult;
import com.treepeople.leapmindtts.pojo.vo.ExerciseVO;
import com.treepeople.leapmindtts.pojo.vo.KnowledgeGraphVO;
import com.treepeople.leapmindtts.pojo.vo.PracticePlanVO;
import com.treepeople.leapmindtts.pojo.vo.RecommendQuestionVO;
import com.treepeople.leapmindtts.pojo.vo.UserWeakPointVO;
import com.treepeople.leapmindtts.pojo.vo.WeakPointDetailVO;
import com.treepeople.leapmindtts.pojo.vo.WeakPointsAnalysisVO;
import com.treepeople.leapmindtts.service.lesson.EventCollectionClient;
import com.treepeople.leapmindtts.service.lesson.WeakPointsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 薄弱点分析服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WeakPointsServiceImpl implements WeakPointsService {

    private final UserWeakPointMapper userWeakPointMapper;
    private final UserExerciseMapper userExerciseMapper;
    private final KnowledgePointMapper knowledgePointMapper;
    private final WebClient webClient;
    private final EventCollectionClient eventCollectionClient;

    @Value("${weak-point.python-service.base-url:http://localhost:8000}")
    private String pythonServiceBaseUrl;

    @Value("${weak-point.python-service.analyze-endpoint:/api/weak-points/analyze}")
    private String analyzeEndpoint;

    @Value("${weak-point.python-service.timeout:30}")
    private int timeoutSeconds;

    @Value("${weak-point.m3-engine.base-url:http://localhost:8001}")
    private String m3EngineBaseUrl;

    @Value("${weak-point.m3-engine.incremental-update-endpoint:/api/weak-points/incremental-update}")
    private String incrementalUpdateEndpoint;

    @Value("${weak-point.m3-engine.timeout:30}")
    private int m3TimeoutSeconds;

    @Value("${weak-point.m3-engine.recommend-endpoint:/api/weak-points/recommend-questions}")
    private String recommendEndpoint;

    @Value("${weak-point.m3-engine.knowledge-graph-endpoint:/api/weak-points/knowledge-graph}")
    private String knowledgeGraphEndpoint;

    @Override
    public PageResult<UserWeakPointVO> getUserWeakPoints(Long userId, String subject, String status,
                                                          Integer page, Integer size) {
        if (userId == null) {
            return PageResult.<UserWeakPointVO>builder()
                    .total(0L).pages(0L).current(1L).size(20L)
                    .records(Collections.emptyList())
                    .build();
        }

        // 应用默认值
        int pageNum  = (page  != null && page  > 0) ? page  : 1;
        int pageSize = (size  != null && size  > 0) ? size  : 20;

        Page<UserWeakPoint> mpPage = new Page<>(pageNum, pageSize);
        Page<UserWeakPoint> result = userWeakPointMapper.selectPageByFilters(
                mpPage, userId, subject, status);

        List<UserWeakPointVO> voList = result.getRecords().stream()
                .map(this::convertToVO)
                .collect(Collectors.toList());

        return PageResult.<UserWeakPointVO>builder()
                .total(result.getTotal())
                .pages(result.getPages())
                .current(result.getCurrent())
                .size(result.getSize())
                .records(voList)
                .build();
    }

    @Override
    public WeakPointsAnalysisVO getOrCreateAnalysis(Long userId) {
        List<UserWeakPoint> weakPoints = userWeakPointMapper.selectActiveByUserId(userId);

        if (weakPoints == null || weakPoints.isEmpty()) {
            return WeakPointsAnalysisVO.builder()
                    .comprehensiveAnalysis("暂无薄弱点数据，无法生成分析报告。")
                    .learningSuggestions("请先完成一些练习，系统将自动分析您的薄弱点。")
                    .detailAnalyses(Collections.emptyList())
                    .recommendedPriority(Collections.emptyList())
                    .build();
        }

        // 24h 缓存检查：如果最新分析时间在 24 小时内，直接返回缓存结果
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime latestAnalyzed = null;
        for (UserWeakPoint wp : weakPoints) {
            if (wp.getAnalyzedAt() != null) {
                if (latestAnalyzed == null || wp.getAnalyzedAt().isAfter(latestAnalyzed)) {
                    latestAnalyzed = wp.getAnalyzedAt();
                }
            }
        }
        if (latestAnalyzed != null && Duration.between(latestAnalyzed, now).toHours() < 24) {
            log.info("AI分析缓存命中: userId={}, analyzedAt={}", userId, latestAnalyzed);
            return buildCachedAnalysisVO(weakPoints);
        }

        // 构建调用 Python AI 服务的请求
        AiAnalysisRequest request = buildAiAnalysisRequest(userId, weakPoints);

        try {
            AiAnalysisResponse response = webClient.post()
                    .uri(pythonServiceBaseUrl + analyzeEndpoint)
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(AiAnalysisResponse.class)
                    .block(Duration.ofSeconds(timeoutSeconds));

            if (response != null && "success".equals(response.getStatus())) {
                // 回写分析结果到数据库
                saveAnalysisResults(weakPoints, response);

                return convertToAnalysisVO(response);
            } else {
                log.warn("AI 分析服务返回错误: {}", response != null ? response.getError() : "null response");
                return buildFallbackAnalysis(weakPoints);
            }
        } catch (Exception e) {
            log.error("调用 Python AI 服务失败", e);
            return buildFallbackAnalysis(weakPoints);
        }
    }

    @Override
    public List<ExerciseVO> recommendExercises(Long userId, String subject, String knowledgePoint, Integer count) {
        if (count == null || count <= 0) {
            count = 5;
        }

        // 1. 查询7天内用户已做过的练习ID（去重排除）
        LocalDateTime sevenDaysAgo = LocalDateTime.now().minusDays(7);
        List<String> recentExerciseIds = userExerciseMapper.selectRecentExerciseIds(userId, sevenDaysAgo);
        Set<String> excludeSet = recentExerciseIds != null
                ? recentExerciseIds.stream().collect(Collectors.toSet())
                : Collections.emptySet();

        // 2. 查询已解决的薄弱点知识（优先推荐）
        List<String> resolvedKnowledgePoints = userWeakPointMapper.selectResolvedKnowledgePoints(userId);

        // 3. 查询活跃的薄弱点（按薄弱程度排序）
        List<UserWeakPoint> activeWeakPoints = userWeakPointMapper.selectActiveByUserId(userId);

        List<ExerciseVO> result = new ArrayList<>();

        // 优先级1：已解决错题的知识点（复习巩固）
        if (resolvedKnowledgePoints != null) {
            for (String kp : resolvedKnowledgePoints) {
                if (result.size() >= count) break;
                if (subject != null) {
                    // 学科过滤
                    boolean matchSubject = activeWeakPoints != null && activeWeakPoints.stream()
                            .anyMatch(wp -> kp.equals(wp.getKnowledgePoint()) && subject.equals(wp.getSubject()));
                    if (!matchSubject) continue;
                }
                if (knowledgePoint != null && !kp.equals(knowledgePoint)) continue;

                String exerciseId = "RESOLVED_" + userId + "_" + kp;
                if (!excludeSet.contains(exerciseId)) {
                    result.add(ExerciseVO.builder()
                            .exerciseId(exerciseId)
                            .knowledgePoint(kp)
                            .subject(subject)
                            .sourceType("RESOLVED_WEAK_POINT")
                            .priority(1)
                            .build());
                }
            }
        }

        // 优先级2：活跃薄弱点（按薄弱程度排序）
        if (activeWeakPoints != null) {
            // HIGH > MEDIUM > LOW
            activeWeakPoints.sort((a, b) -> {
                int levelCompare = getWeaknessLevelWeight(b.getWeaknessLevel())
                        - getWeaknessLevelWeight(a.getWeaknessLevel());
                if (levelCompare != 0) return levelCompare;
                return Integer.compare(
                        b.getErrorCount() != null ? b.getErrorCount() : 0,
                        a.getErrorCount() != null ? a.getErrorCount() : 0);
            });

            for (UserWeakPoint wp : activeWeakPoints) {
                if (result.size() >= count) break;
                if (subject != null && !subject.equals(wp.getSubject())) continue;
                if (knowledgePoint != null && !knowledgePoint.equals(wp.getKnowledgePoint())) continue;

                String exerciseId = "ACTIVE_" + userId + "_" + wp.getKnowledgePoint();
                if (!excludeSet.contains(exerciseId)) {
                    result.add(ExerciseVO.builder()
                            .exerciseId(exerciseId)
                            .knowledgePoint(wp.getKnowledgePoint())
                            .subject(wp.getSubject())
                            .sourceType("ACTIVE_WEAK_POINT")
                            .priority(2)
                            .build());
                }
            }
        }

        return result;
    }

    @Override
    @Transactional
    public void recordExerciseResult(ExerciseRecordRequest request) {
        // 1. 记录练习结果
        UserExercise exercise = UserExercise.builder()
                .userId(request.getUserId())
                .exerciseId(request.getExerciseId())
                .knowledgePoint(request.getKnowledgePoint())
                .subject(request.getSubject())
                .isCorrect(request.getIsCorrect())
                .completedAt(LocalDateTime.now())
                .build();
        userExerciseMapper.insert(exercise);

        // 2. 更新薄弱点数据（先捕获旧分数，更新后再计算新分数）
        UserWeakPoint weakPoint = findOrCreateWeakPoint(request);
        BigDecimal oldScore = calculateWeaknessScore(weakPoint);
        boolean isNewRecord = weakPoint.getId() == null;

        if (request.getIsCorrect() != null && request.getIsCorrect() == 1) {
            // 答对了：增加总计数
            weakPoint.setTotalCount((weakPoint.getTotalCount() != null ? weakPoint.getTotalCount() : 0) + 1);
            weakPoint.setTotalAttempts(weakPoint.getTotalCount()); // 同步 Python 字段
            // 重新计算正确率
            int total = weakPoint.getTotalCount();
            int errors = weakPoint.getErrorCount() != null ? weakPoint.getErrorCount() : 0;
            int correct = total - errors;
            if (total > 0) {
                weakPoint.setAccuracyRate(BigDecimal.valueOf(correct * 100.0 / total)
                        .setScale(2, java.math.RoundingMode.HALF_UP));
            }
            // 如果正确率 >= 80%，标记为已解决
            if (weakPoint.getAccuracyRate() != null && weakPoint.getAccuracyRate().compareTo(new BigDecimal("80")) >= 0) {
                weakPoint.setStatus("RESOLVED");
            }
        } else {
            // 答错了：增加错误计数
            weakPoint.setErrorCount((weakPoint.getErrorCount() != null ? weakPoint.getErrorCount() : 0) + 1);
            weakPoint.setTotalCount((weakPoint.getTotalCount() != null ? weakPoint.getTotalCount() : 0) + 1);
            weakPoint.setTotalAttempts(weakPoint.getTotalCount()); // 同步 Python 字段
            int total = weakPoint.getTotalCount();
            int errors = weakPoint.getErrorCount();
            if (total > 0) {
                weakPoint.setAccuracyRate(BigDecimal.valueOf((total - errors) * 100.0 / total)
                        .setScale(2, java.math.RoundingMode.HALF_UP));
            }
            weakPoint.setLastErrorTime(LocalDateTime.now());
            weakPoint.setLastErrorAt(LocalDateTime.now()); // 同步 Python 字段
            weakPoint.setStatus("ACTIVE");
        }

        if (weakPoint.getId() != null) {
            userWeakPointMapper.updateById(weakPoint);
        } else {
            userWeakPointMapper.insert(weakPoint);
        }

        // 3. 上报 weak_point_changed 事件（仅当薄弱点已存在且有变化时）
        if (!isNewRecord) {
            BigDecimal newScore = calculateWeaknessScore(weakPoint);
            String reason = (request.getIsCorrect() != null && request.getIsCorrect() == 0)
                    ? "REPEATED_ERROR" : "ACCURACY_DROP";
            eventCollectionClient.reportWeakPointChanged(
                    request.getUserId(),
                    weakPoint.getKnowledgePoint(),
                    oldScore,
                    newScore,
                    reason);
        }

        // 4. 调用 Python M3 引擎增量更新（异步，不阻塞主流程）
        triggerM3IncrementalUpdate(request.getUserId(), weakPoint.getKpId());

        log.info("练习记录已保存: userId={}, exerciseId={}, isCorrect={}, knowledgePoint={}",
                request.getUserId(), request.getExerciseId(), request.getIsCorrect(), request.getKnowledgePoint());
    }

    // ==================== 私有辅助方法 ====================

    private UserWeakPointVO convertToVO(UserWeakPoint entity) {
        UserWeakPointVO vo = new UserWeakPointVO();
        BeanUtils.copyProperties(entity, vo);
        return vo;
    }

    private UserWeakPoint findOrCreateWeakPoint(ExerciseRecordRequest request) {
        // 按用户+知识点精确查询已有记录（O(1) 数据库查询替代 O(n) 全表扫描+循环）
        if (request.getKnowledgePoint() != null) {
            UserWeakPoint existing = userWeakPointMapper.selectByUserIdAndKnowledgePoint(
                    request.getUserId(), request.getKnowledgePoint());
            if (existing != null) {
                // 兼容旧数据：如果 kpId 为空，回填之（旧版本创建记录时没有设置 kpId）
                if (existing.getKpId() == null) {
                    Long kpId = knowledgePointMapper.selectIdByName(existing.getKnowledgePoint());
                    if (kpId != null) {
                        existing.setKpId(kpId);
                        userWeakPointMapper.updateById(existing);
                    }
                }
                return existing;
            }
        }

        // 创建新记录 — 补上 kpId，确保首次练习也能触发增量更新
        String kpName = request.getKnowledgePoint() != null ? request.getKnowledgePoint() : "未知知识点";
        Long kpId = knowledgePointMapper.selectIdByName(kpName);
        return UserWeakPoint.builder()
                .userId(request.getUserId())
                .kpId(kpId)
                .knowledgePoint(kpName)
                .subject(request.getSubject())
                .weaknessLevel("MEDIUM")
                .errorCount(0)
                .totalCount(0)
                .totalAttempts(0)
                .status("ACTIVE")
                .build();
    }

    private AiAnalysisRequest buildAiAnalysisRequest(Long userId, List<UserWeakPoint> weakPoints) {
        List<AiAnalysisRequest.WeakPointItem> items = weakPoints.stream()
                .map(wp -> AiAnalysisRequest.WeakPointItem.builder()
                        .id(wp.getId())
                        .knowledgePoint(wp.getKnowledgePoint())
                        .subject(wp.getSubject())
                        .weaknessLevel(wp.getWeaknessLevel())
                        .errorCount(wp.getErrorCount())
                        .totalCount(wp.getTotalCount())
                        .accuracyRate(wp.getAccuracyRate())
                        .build())
                .collect(Collectors.toList());

        // 获取最近的练习记录
        LocalDateTime thirtyDaysAgo = LocalDateTime.now().minusDays(30);
        List<UserExercise> recentExercises = userExerciseMapper.selectByTimeRange(userId, thirtyDaysAgo, LocalDateTime.now());
        List<AiAnalysisRequest.ExerciseRecordItem> exerciseItems = recentExercises != null
                ? recentExercises.stream().map(e -> AiAnalysisRequest.ExerciseRecordItem.builder()
                        .exerciseId(e.getExerciseId())
                        .knowledgePoint(e.getKnowledgePoint())
                        .subject(e.getSubject())
                        .isCorrect(e.getIsCorrect())
                        .completedAt(e.getCompletedAt() != null ? e.getCompletedAt().toString() : null)
                        .build())
                .collect(Collectors.toList())
                : Collections.emptyList();

        return AiAnalysisRequest.builder()
                .userId(userId)
                .weakPoints(items)
                .recentExercises(exerciseItems)
                .language("zh")
                .build();
    }

    private void saveAnalysisResults(List<UserWeakPoint> weakPoints, AiAnalysisResponse response) {
        // 将 detailAnalyses 转为 Map，O(n+m) 替代原有的 O(n*m) 嵌套循环
        java.util.Map<String, AiAnalysisResponse.DetailAnalysis> analysisMap =
                new java.util.HashMap<>();
        if (response.getDetailAnalyses() != null) {
            for (AiAnalysisResponse.DetailAnalysis da : response.getDetailAnalyses()) {
                if (da.getKnowledgePoint() != null) {
                    analysisMap.put(da.getKnowledgePoint(), da);
                }
            }
        }

        for (UserWeakPoint wp : weakPoints) {
            AiAnalysisResponse.DetailAnalysis da = analysisMap.get(wp.getKnowledgePoint());
            if (da != null) {
                userWeakPointMapper.updateAiAnalysis(
                        wp.getId(),
                        da.getAnalysis(),
                        da.getSuggestion());
            }
        }
    }

    private WeakPointsAnalysisVO convertToAnalysisVO(AiAnalysisResponse response) {
        List<WeakPointsAnalysisVO.DetailItem> details = new ArrayList<>();
        if (response.getDetailAnalyses() != null) {
            for (AiAnalysisResponse.DetailAnalysis da : response.getDetailAnalyses()) {
                details.add(WeakPointsAnalysisVO.DetailItem.builder()
                        .knowledgePoint(da.getKnowledgePoint())
                        .analysis(da.getAnalysis())
                        .suggestion(da.getSuggestion())
                        .build());
            }
        }

        return WeakPointsAnalysisVO.builder()
                .comprehensiveAnalysis(response.getComprehensiveAnalysis())
                .learningSuggestions(response.getLearningSuggestions())
                .detailAnalyses(details)
                .recommendedPriority(response.getRecommendedPriority())
                .build();
    }

    /**
     * 从数据库缓存的 ai_analysis/ai_suggestion 字段构建分析 VO
     * 用于 24 小时内的缓存命中场景，避免重复调用 Python AI 服务
     */
    private WeakPointsAnalysisVO buildCachedAnalysisVO(List<UserWeakPoint> weakPoints) {
        StringBuilder comprehensive = new StringBuilder("## 薄弱点分析（24h缓存）\n\n");

        List<WeakPointsAnalysisVO.DetailItem> details = new ArrayList<>();
        for (UserWeakPoint wp : weakPoints) {
            if (wp.getAiAnalysis() != null) {
                comprehensive.append("- **").append(wp.getKnowledgePoint())
                        .append("**: ").append(wp.getAiAnalysis()).append("\n");
            }
            details.add(WeakPointsAnalysisVO.DetailItem.builder()
                    .knowledgePoint(wp.getKnowledgePoint())
                    .analysis(wp.getAiAnalysis())
                    .suggestion(wp.getAiSuggestion())
                    .build());
        }

        // 推荐优先级：按薄弱程度降序
        List<String> priority = weakPoints.stream()
                .sorted((a, b) -> Integer.compare(
                        getWeaknessLevelWeight(b.getWeaknessLevel()),
                        getWeaknessLevelWeight(a.getWeaknessLevel())))
                .map(UserWeakPoint::getKnowledgePoint)
                .collect(Collectors.toList());

        return WeakPointsAnalysisVO.builder()
                .comprehensiveAnalysis(comprehensive.toString())
                .learningSuggestions("以下分析基于缓存数据（最近24小时内生成）。持续练习后将自动刷新分析。")
                .detailAnalyses(details)
                .recommendedPriority(priority)
                .build();
    }

    private WeakPointsAnalysisVO buildFallbackAnalysis(List<UserWeakPoint> weakPoints) {
        StringBuilder sb = new StringBuilder("## 薄弱点分析\n\n");
        sb.append("以下是根据您的练习数据识别的薄弱点：\n\n");

        List<String> priority = new ArrayList<>();
        for (UserWeakPoint wp : weakPoints) {
            sb.append("- **").append(wp.getKnowledgePoint()).append("**")
                    .append("（").append(wp.getSubject() != null ? wp.getSubject() : "未知学科").append("）")
                    .append("：错误").append(wp.getErrorCount()).append("次")
                    .append("，薄弱程度").append(getWeaknessLevelLabel(wp.getWeaknessLevel())).append("\n");
            priority.add(wp.getKnowledgePoint());
        }

        sb.append("\n### 学习建议\n\n");
        sb.append("建议优先复习以上知识点，每天坚持练习，逐步提升。\n");

        return WeakPointsAnalysisVO.builder()
                .comprehensiveAnalysis(sb.toString())
                .learningSuggestions("建议每天针对薄弱知识点进行专项练习，每次练习后及时订正错题。")
                .detailAnalyses(Collections.emptyList())
                .recommendedPriority(priority)
                .build();
    }

    private int getWeaknessLevelWeight(String level) {
        if ("HIGH".equalsIgnoreCase(level)) return 3;
        if ("MEDIUM".equalsIgnoreCase(level)) return 2;
        if ("LOW".equalsIgnoreCase(level)) return 1;
        return 0;
    }

    /**
     * 计算薄弱度分数（0-1 范围，供 M6 画像引擎使用）
     * <p>
     * 优先使用 Python 引擎计算的权威 {@code weakness_score}；
     * 若 Python 尚未计算（新记录），回退到简易公式：1 - accuracy/100；
     * 无正确率时用薄弱等级估算。
     *
     * @param wp 薄弱点记录
     * @return 薄弱度分数 0-1
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
                    wp.getAccuracyRate().divide(BigDecimal.valueOf(100), 4, java.math.RoundingMode.HALF_UP));
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

    private String getWeaknessLevelLabel(String level) {
        if ("HIGH".equalsIgnoreCase(level)) return "高";
        if ("MEDIUM".equalsIgnoreCase(level)) return "中";
        if ("LOW".equalsIgnoreCase(level)) return "低";
        return level;
    }

    /**
     * 异步触发 Python M3 引擎增量更新
     * <p>
     * 用户每次提交练习后，通知 Python 引擎对该知识点进行三维加权重算。
     * 采用 fire-and-forget 模式：调用失败仅记录日志，不影响主流程响应。
     *
     * @param userId 用户ID
     * @param kpId   知识点ID（可为 null，为 null 时跳过）
     */
    private void triggerM3IncrementalUpdate(Long userId, Long kpId) {
        if (kpId == null) {
            return;
        }
        try {
            java.util.Map<String, Object> body = new java.util.LinkedHashMap<>();
            body.put("userId", userId);
            body.put("kpIds", java.util.Collections.singletonList(kpId));

            webClient.post()
                    .uri(m3EngineBaseUrl + incrementalUpdateEndpoint)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(String.class)
                    .subscribe(
                            result -> log.debug("M3 增量更新成功: userId={}, kpId={}", userId, kpId),
                            error -> log.warn("M3 增量更新失败(非阻塞): userId={}, kpId={}, error={}",
                                    userId, kpId, error.getMessage())
                    );
        } catch (Exception e) {
            log.warn("M3 增量更新调用异常(非阻塞): userId={}, kpId={}, error={}",
                    userId, kpId, e.getMessage());
        }
    }

    // ==================== 推荐题目 + 知识图谱 ====================

    @Override
    public List<RecommendQuestionVO> recommendQuestions(Long userId, String knowledgePoint, Integer count) {
        if (count == null || count <= 0) {
            count = 5;
        }

        // 1. 名称 → kpId 翻译（对外用名称，内部 Python 用数字 ID）
        Long kpId = knowledgePointMapper.selectIdByName(knowledgePoint);
        if (kpId == null) {
            log.warn("知识点名称不存在，无法推荐: knowledgePoint={}", knowledgePoint);
            return Collections.emptyList();
        }

        // 2. 调用 Python M3 引擎推荐真实题目（基于题库 questions 表，排除已做，按难度排序）
        try {
            String uri = m3EngineBaseUrl + recommendEndpoint
                    + "?user_id=" + userId + "&kp_id=" + kpId + "&count=" + count;

            @SuppressWarnings("unchecked")
            java.util.Map<String, Object> response = webClient.get()
                    .uri(uri)
                    .retrieve()
                    .bodyToMono(java.util.Map.class)
                    .block(Duration.ofSeconds(m3TimeoutSeconds));

            if (response != null && response.containsKey("questions")) {
                String kpName = (String) response.getOrDefault("kpName", knowledgePoint);
                @SuppressWarnings("unchecked")
                java.util.List<java.util.Map<String, Object>> questions =
                        (java.util.List<java.util.Map<String, Object>>) response.get("questions");

                if (questions != null) {
                    List<RecommendQuestionVO> result = new ArrayList<>();
                    for (java.util.Map<String, Object> q : questions) {
                        result.add(RecommendQuestionVO.builder()
                                .questionId(String.valueOf(q.get("id")))
                                .knowledgePoint(kpName)
                                .subject(null)  // Python 响应不含 subject，后续可扩展
                                .difficulty(mapDifficulty(q.get("difficulty")))
                                .questionType(null)  // Python 响应不含题型，后续可扩展
                                .questionTitle(truncateContent((String) q.get("content"), 60))
                                .reason("基于薄弱点「" + kpName + "」的针对性推荐")
                                .build());
                    }
                    return result;
                }
            }
            log.warn("Python M3 推荐题目返回空: userId={}, kpId={}, knowledgePoint={}", userId, kpId, knowledgePoint);
        } catch (Exception e) {
            log.error("调用 Python M3 推荐题目失败: userId={}, kpId={}, knowledgePoint={}, error={}",
                    userId, kpId, knowledgePoint, e.getMessage());
        }

        return Collections.emptyList();
    }

    /** 将 Python 返回的 difficulty 数值映射为难度标签（V5 题库：1-5 整数） */
    private String mapDifficulty(Object difficulty) {
        if (difficulty == null) return "MEDIUM";
        int d = difficulty instanceof Number ? ((Number) difficulty).intValue() : 3;
        if (d <= 2) return "EASY";
        if (d <= 3) return "MEDIUM";
        return "HARD";  // 4-5
    }

    /** 截断过长内容用作题目摘要 */
    private String truncateContent(String content, int maxLen) {
        if (content == null) return "";
        return content.length() <= maxLen ? content : content.substring(0, maxLen) + "...";
    }

    @Override
    public KnowledgeGraphVO getKnowledgeGraph(Long userId, String subject) {
        // 调用 Python M3 引擎获取知识图谱（基于 knowledge_points 表 parent_id 构建真实依赖树）
        try {
            StringBuilder uri = new StringBuilder(m3EngineBaseUrl + knowledgeGraphEndpoint)
                    .append("?user_id=").append(userId);
            if (subject != null && !subject.isEmpty()) {
                uri.append("&subject=").append(subject);
            }

            @SuppressWarnings("unchecked")
            java.util.Map<String, Object> response = webClient.get()
                    .uri(uri.toString())
                    .retrieve()
                    .bodyToMono(java.util.Map.class)
                    .block(Duration.ofSeconds(m3TimeoutSeconds));

            if (response != null) {
                @SuppressWarnings("unchecked")
                java.util.List<java.util.Map<String, Object>> rawNodes =
                        (java.util.List<java.util.Map<String, Object>>) response.get("nodes");
                @SuppressWarnings("unchecked")
                java.util.List<java.util.Map<String, Object>> rawEdges =
                        (java.util.List<java.util.Map<String, Object>>) response.get("edges");

                List<KnowledgeGraphVO.GraphNode> nodes = new ArrayList<>();
                if (rawNodes != null) {
                    for (java.util.Map<String, Object> n : rawNodes) {
                        nodes.add(KnowledgeGraphVO.GraphNode.builder()
                                .id(String.valueOf(n.get("id")))
                                .name((String) n.get("name"))
                                .subject((String) n.getOrDefault("subject", subject))
                                .weaknessLevel((String) n.getOrDefault("weaknessLevel", "UNKNOWN"))
                                .masteryRate(toDouble(n.get("weaknessScore"), 0.0))
                                .group((String) n.getOrDefault("subject", subject))
                                .build());
                    }
                }

                List<KnowledgeGraphVO.GraphEdge> edges = new ArrayList<>();
                if (rawEdges != null) {
                    for (java.util.Map<String, Object> e : rawEdges) {
                        edges.add(KnowledgeGraphVO.GraphEdge.builder()
                                .source(String.valueOf(e.get("source")))
                                .target(String.valueOf(e.get("target")))
                                .relation((String) e.getOrDefault("relation", "parent_child"))
                                .build());
                    }
                }

                return KnowledgeGraphVO.builder()
                        .nodes(nodes)
                        .edges(edges)
                        .build();
            }
            log.warn("Python M3 知识图谱返回空: userId={}, subject={}", userId, subject);
        } catch (Exception e) {
            log.error("调用 Python M3 知识图谱失败: userId={}, subject={}, error={}",
                    userId, subject, e.getMessage());
        }

        return KnowledgeGraphVO.builder()
                .nodes(Collections.emptyList())
                .edges(Collections.emptyList())
                .build();
    }

    /** 安全地将 Object 转为 double，null 时返回默认值 */
    private double toDouble(Object value, double defaultValue) {
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        if (value instanceof String) {
            try {
                return Double.parseDouble((String) value);
            } catch (NumberFormatException e) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    // ==================== 薄弱点详情 + 练习计划 ====================

    @Override
    public WeakPointDetailVO getWeakPointDetail(Long id) {
        if (id == null) {
            throw new IllegalArgumentException("薄弱点ID不能为空");
        }

        UserWeakPoint wp = userWeakPointMapper.selectById(id);
        if (wp == null) {
            throw new IllegalArgumentException("薄弱点记录不存在: id=" + id);
        }

        // 1. 基本信息（直接从实体映射，含 Python 引擎字段）
        WeakPointDetailVO.WeakPointDetailVOBuilder builder = WeakPointDetailVO.builder()
                .id(wp.getId())
                .userId(wp.getUserId())
                .knowledgePoint(wp.getKnowledgePoint())
                .subject(wp.getSubject())
                .weaknessLevel(wp.getWeaknessLevel())
                .weaknessScore(wp.getWeaknessScore())
                .errorCount(wp.getErrorCount())
                .totalCount(wp.getTotalCount())
                .accuracyRate(wp.getAccuracyRate())
                .errorRate(wp.getErrorRate())
                .recentCorrectRate(wp.getRecentCorrectRate())
                .confusionCount(wp.getConfusionCount())
                .lastErrorTime(wp.getLastErrorTime())
                .status(wp.getStatus())
                .aiAnalysis(wp.getAiAnalysis())
                .aiSuggestion(wp.getAiSuggestion())
                .analyzedAt(wp.getAnalyzedAt())
                .calculatedAt(wp.getCalculatedAt())
                .createdAt(wp.getCreatedAt());

        // 2. 近期错题（最近20条该知识点的练习记录）
        List<UserExercise> exercises = userExerciseMapper.selectByUserIdAndKp(
                wp.getUserId(), wp.getKnowledgePoint());
        if (exercises != null && !exercises.isEmpty()) {
            List<WeakPointDetailVO.ErrorExerciseItem> errorItems = exercises.stream()
                    .limit(20)
                    .map(e -> WeakPointDetailVO.ErrorExerciseItem.builder()
                            .id(e.getId())
                            .exerciseId(e.getExerciseId())
                            .isCorrect(e.getIsCorrect())
                            .completedAt(e.getCompletedAt())
                            .build())
                    .collect(Collectors.toList());
            builder.recentErrors(errorItems);

            // 3. 趋势：优先 Python 引擎计算的 trend，无数据时实时计算
            if (wp.getTrend() != null && !wp.getTrend().isEmpty()) {
                builder.trend(wp.getTrend());
            } else {
                computeTrendData(builder, exercises);
            }
        } else {
            builder.recentErrors(Collections.emptyList());
            if (wp.getTrend() != null && !wp.getTrend().isEmpty()) {
                builder.trend(wp.getTrend());
            } else {
                builder.trend("stable");
            }
        }

        return builder.build();
    }

    /**
     * 基于练习记录计算趋势数据（近7天 vs 前7天错误率）
     */
    private void computeTrendData(WeakPointDetailVO.WeakPointDetailVOBuilder builder,
                                  List<UserExercise> exercises) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime recentStart = now.minusDays(7);
        LocalDateTime previousStart = now.minusDays(14);

        long recentTotal = 0, recentErrors = 0;
        long previousTotal = 0, previousErrors = 0;

        for (UserExercise e : exercises) {
            if (e.getCompletedAt() == null) continue;
            if (!e.getCompletedAt().isBefore(recentStart)) {
                // 近7天
                recentTotal++;
                if (e.getIsCorrect() != null && e.getIsCorrect() == 0) recentErrors++;
            } else if (!e.getCompletedAt().isBefore(previousStart)) {
                // 前7天
                previousTotal++;
                if (e.getIsCorrect() != null && e.getIsCorrect() == 0) previousErrors++;
            }
        }

        BigDecimal recentErrorRate = recentTotal > 0
                ? BigDecimal.valueOf(recentErrors * 100.0 / recentTotal)
                    .setScale(2, java.math.RoundingMode.HALF_UP)
                : null;
        BigDecimal previousErrorRate = previousTotal > 0
                ? BigDecimal.valueOf(previousErrors * 100.0 / previousTotal)
                    .setScale(2, java.math.RoundingMode.HALF_UP)
                : null;

        builder.recentErrorRate(recentErrorRate);
        builder.previousErrorRate(previousErrorRate);

        // 判断趋势：变化超过5个百分点才判定
        if (recentErrorRate == null || previousErrorRate == null) {
            builder.trend("stable");
        } else if (recentErrorRate.compareTo(previousErrorRate.add(BigDecimal.valueOf(5))) > 0) {
            builder.trend("declining");
        } else if (recentErrorRate.compareTo(previousErrorRate.subtract(BigDecimal.valueOf(5))) < 0) {
            builder.trend("improving");
        } else {
            builder.trend("stable");
        }
    }

    @Override
    public PracticePlanVO generatePracticePlan(PracticePlanRequest request) {
        // 1. 查询每个目标知识点的薄弱点状态（内部翻译名称→kpId 后调用 Python M3 引擎）
        List<RecommendQuestionVO> allQuestions = new ArrayList<>();
        for (String kpName : request.getKnowledgePoints()) {
            List<RecommendQuestionVO> kpQuestions = recommendQuestions(
                    request.getUserId(), kpName, 5);
            allQuestions.addAll(kpQuestions);
        }

        // 2. 构建计划题目列表（按难度排序：EASY → MEDIUM → HARD）
        allQuestions.sort((a, b) -> {
            int da = difficultyOrder(a.getDifficulty());
            int db = difficultyOrder(b.getDifficulty());
            return Integer.compare(da, db);
        });

        List<PracticePlanVO.PlanQuestionItem> items = new ArrayList<>();
        for (int i = 0; i < allQuestions.size(); i++) {
            RecommendQuestionVO q = allQuestions.get(i);
            items.add(PracticePlanVO.PlanQuestionItem.builder()
                    .questionId(q.getQuestionId())
                    .knowledgePoint(q.getKnowledgePoint())
                    .subject(q.getSubject())
                    .difficulty(q.getDifficulty())
                    .questionType(q.getQuestionType())
                    .questionTitle(q.getQuestionTitle())
                    .order(i + 1)
                    .reason(q.getReason())
                    .build());
        }

        // 3. 预计时间：每题平均45秒 + 订正30秒
        int estimatedMinutes = Math.max(1,
                (int) Math.ceil(allQuestions.size() * 75.0 / 60));

        // 从第一个推荐结果的 kpName 获取目标知识点名称
        List<String> targetKpNames = allQuestions.stream()
                .map(RecommendQuestionVO::getKnowledgePoint)
                .distinct()
                .collect(Collectors.toList());

        return PracticePlanVO.builder()
                .userId(request.getUserId())
                .targetKnowledgePoints(targetKpNames)
                .totalQuestions(items.size())
                .estimatedMinutes(estimatedMinutes)
                .questions(items)
                .build();
    }

    private int difficultyOrder(String difficulty) {
        if ("EASY".equalsIgnoreCase(difficulty)) return 1;
        if ("MEDIUM".equalsIgnoreCase(difficulty)) return 2;
        if ("HARD".equalsIgnoreCase(difficulty)) return 3;
        return 2;
    }
}
