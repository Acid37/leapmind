package com.treepeople.leapmindtts.service.lesson.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
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
    private final WebClient webClient;
    private final EventCollectionClient eventCollectionClient;

    @Value("${weak-point.python-service.base-url:http://localhost:8000}")
    private String pythonServiceBaseUrl;

    @Value("${weak-point.python-service.analyze-endpoint:/api/weak-points/analyze}")
    private String analyzeEndpoint;

    @Value("${weak-point.python-service.timeout:30}")
    private int timeoutSeconds;

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
                return existing;
            }
        }

        // 创建新记录
        return UserWeakPoint.builder()
                .userId(request.getUserId())
                .knowledgePoint(request.getKnowledgePoint() != null ? request.getKnowledgePoint() : "未知知识点")
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

    // ==================== 推荐题目 + 知识图谱 ====================

    @Override
    public List<RecommendQuestionVO> recommendQuestions(Long userId, String knowledgePoint, Integer count) {
        if (count == null || count <= 0) {
            count = 5;
        }

        // 精确查询该知识点的薄弱点记录（SQL 级别，O(1) 替代全表扫描 O(n)）
        UserWeakPoint target = userWeakPointMapper.selectByUserIdAndKnowledgePoint(userId, knowledgePoint);

        // 确定题目难度：薄弱程度高→基础题，中→中等题，低→提高题
        String difficulty;
        String reason;
        if (target != null) {
            switch (target.getWeaknessLevel() != null ? target.getWeaknessLevel().toUpperCase() : "MEDIUM") {
                case "HIGH":
                    difficulty = "EASY";
                    reason = "该知识点错误率较高，建议从基础题开始巩固";
                    break;
                case "MEDIUM":
                    difficulty = "MEDIUM";
                    reason = "该知识点掌握一般，建议进行中等难度练习";
                    break;
                default:
                    difficulty = "HARD";
                    reason = "该知识点掌握较好，建议挑战提高题";
            }
        } else {
            difficulty = "MEDIUM";
            reason = "暂无该知识点的练习记录";
        }

        List<RecommendQuestionVO> questions = new ArrayList<>();
        String[] questionTypes = {"选择题", "填空题", "解答题"};
        String[] templates = {
            "关于「%s」的基础练习题",
            "「%s」的综合应用题",
            "「%s」的易错题训练",
            "「%s」的巩固练习题",
            "「%s」的能力提升题"
        };

        for (int i = 0; i < count; i++) {
            questions.add(RecommendQuestionVO.builder()
                    .questionId("Q_" + userId + "_" + sanitizeKey(knowledgePoint) + "_" + (i + 1))
                    .knowledgePoint(knowledgePoint)
                    .subject(target != null ? target.getSubject() : null)
                    .difficulty(difficulty)
                    .questionType(questionTypes[i % questionTypes.length])
                    .questionTitle(String.format(templates[i % templates.length], knowledgePoint))
                    .reason(reason)
                    .build());
        }

        return questions;
    }

    @Override
    public KnowledgeGraphVO getKnowledgeGraph(Long userId, String subject) {
        List<UserWeakPoint> weakPoints;
        if (subject != null && !subject.isEmpty()) {
            weakPoints = userWeakPointMapper.selectByUserIdAndSubject(userId, subject);
        } else {
            weakPoints = userWeakPointMapper.selectByUserId(userId);
        }

        // 构建节点
        List<KnowledgeGraphVO.GraphNode> nodes = new ArrayList<>();
        if (weakPoints != null) {
            for (UserWeakPoint wp : weakPoints) {
                double mastery = wp.getAccuracyRate() != null ? wp.getAccuracyRate().doubleValue() : 0.0;
                String level = wp.getWeaknessLevel();
                // 未练习过的标记为 UNKNOWN
                if (wp.getTotalCount() == null || wp.getTotalCount() == 0) {
                    level = "UNKNOWN";
                    mastery = 0.0;
                } else if (mastery >= 80.0) {
                    level = "MASTERED";
                }

                nodes.add(KnowledgeGraphVO.GraphNode.builder()
                        .id(wp.getKnowledgePoint())
                        .name(wp.getKnowledgePoint())
                        .subject(wp.getSubject())
                        .weaknessLevel(level)
                        .masteryRate(mastery)
                        .group(wp.getSubject() != null ? wp.getSubject() : "其他")
                        .build());
            }
        }

        // 构建边：同科知识点按难度排序后建立前后连接
        List<KnowledgeGraphVO.GraphEdge> edges = new ArrayList<>();

        // 按学科分组构建知识链路
        java.util.Map<String, java.util.List<UserWeakPoint>> bySubject = new java.util.LinkedHashMap<>();
        if (weakPoints != null) {
            for (UserWeakPoint wp : weakPoints) {
                String s = wp.getSubject() != null ? wp.getSubject() : "其他";
                bySubject.computeIfAbsent(s, k -> new ArrayList<>()).add(wp);
            }
        }

        for (java.util.List<UserWeakPoint> sameSubject : bySubject.values()) {
            // 同科知识点按薄弱程度排序：HIGH→MEDIUM→LOW→MASTERED
            sameSubject.sort((a, b) -> {
                int la = getWeaknessLevelWeight(a.getWeaknessLevel());
                int lb = getWeaknessLevelWeight(b.getWeaknessLevel());
                return Integer.compare(lb, la); // 降序：掌握差的在前
            });

            // 前后知识点建立 prerequisite 关系
            for (int i = 0; i < sameSubject.size() - 1; i++) {
                UserWeakPoint prev = sameSubject.get(i);
                UserWeakPoint next = sameSubject.get(i + 1);
                edges.add(KnowledgeGraphVO.GraphEdge.builder()
                        .source(prev.getKnowledgePoint())
                        .target(next.getKnowledgePoint())
                        .relation("prerequisite")
                        .build());
            }
        }

        return KnowledgeGraphVO.builder()
                .nodes(nodes)
                .edges(edges)
                .build();
    }

    private String sanitizeKey(String s) {
        if (s == null) return "unknown";
        return s.replaceAll("[^a-zA-Z0-9_\\u4e00-\\u9fa5]", "_");
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
        // 1. 查询每个目标知识点的薄弱点状态
        List<RecommendQuestionVO> allQuestions = new ArrayList<>();
        for (String kp : request.getKnowledgePoints()) {
            List<RecommendQuestionVO> kpQuestions = recommendQuestions(
                    request.getUserId(), kp, 5);
            for (int i = 0; i < kpQuestions.size(); i++) {
                RecommendQuestionVO q = kpQuestions.get(i);
                allQuestions.add(q);
            }
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

        return PracticePlanVO.builder()
                .userId(request.getUserId())
                .targetKnowledgePoints(request.getKnowledgePoints())
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
