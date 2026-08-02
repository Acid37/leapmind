package com.treepeople.leapmindtts.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.treepeople.leapmindtts.controller.PracticeController;
import com.treepeople.leapmindtts.mapper.PracticeAnswerRecordMapper;
import com.treepeople.leapmindtts.mapper.PracticeCheckinMapper;
import com.treepeople.leapmindtts.mapper.PracticeMistakeMapper;
import com.treepeople.leapmindtts.mapper.PracticeQuestionMapper;
import com.treepeople.leapmindtts.mapper.PracticeUserStatsMapper;
import com.treepeople.leapmindtts.mapper.UserMapper;
import com.treepeople.leapmindtts.pojo.entity.PracticeAnswerRecord;
import com.treepeople.leapmindtts.pojo.entity.PracticeCheckin;
import com.treepeople.leapmindtts.pojo.entity.PracticeMistake;
import com.treepeople.leapmindtts.pojo.entity.PracticeQuestion;
import com.treepeople.leapmindtts.pojo.entity.PracticeUserStats;
import com.treepeople.leapmindtts.pojo.entity.User;
import com.treepeople.leapmindtts.service.AIModelService;
import com.treepeople.leapmindtts.service.PracticeService;
import com.treepeople.leapmindtts.service.importer.PracticeQuestionImportParser;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PracticeServiceImpl implements PracticeService {

    private static final int DAILY_TARGET = 5;
    private static final int DAILY_BONUS = 10;
    private static final int CHECKIN_POINTS = 2;
    private static final String STATUS_ENABLED = "ENABLED";
    private static final String STATUS_DISABLED = "DISABLED";
    private static final String STATUS_ARCHIVED = "ARCHIVED";
    private static final String STATUS_UNRESOLVED = "UNRESOLVED";
    private static final String STATUS_REVIEWING = "REVIEWING";
    private static final String STATUS_RESOLVED = "RESOLVED";
    private static final String MODE_SEQUENTIAL = "SEQUENTIAL";
    private static final String MODE_RANDOM = "RANDOM";
    private static final String MODE_MISTAKES = "MISTAKES";
    private static final String MODE_FREE = "FREE_PRACTICE";
    private static final String MODE_AFTER_CLASS = "AFTER_CLASS";
    private static final String MODE_MISTAKE_REDO = "MISTAKE_REDO";
    private static final int RECENT_DEDUP_DAYS = 7;

    private final PracticeQuestionMapper questionMapper;
    private final PracticeAnswerRecordMapper recordMapper;
    private final PracticeUserStatsMapper statsMapper;
    private final PracticeMistakeMapper mistakeMapper;
    private final PracticeCheckinMapper checkinMapper;
    private final UserMapper userMapper;
    private final PracticeQuestionImportParser questionImportParser;
    private final ObjectProvider<StringRedisTemplate> redisTemplateProvider;
    private final ObjectProvider<AIModelService> aiModelServiceProvider;

    @Override
    public Map<String, Object> getFilters() {
        Map<String, Object> filters = new HashMap<>();
        filters.put("subjects", distinctColumn("subject"));
        filters.put("gradeLevels", distinctColumn("grade_level"));
        filters.put("tracks", distinctColumn("track"));
        filters.put("chapters", distinctColumn("chapter"));
        filters.put("knowledgePoints", distinctColumn("knowledge_point"));
        filters.put("questionTypes", List.of("SINGLE_CHOICE", "MULTIPLE_CHOICE", "FILL_BLANK", "SHORT_ANSWER"));
        filters.put("difficulties", List.of("BASIC", "ADVANCED", "HARD"));
        filters.put("modes", List.of(MODE_FREE, MODE_AFTER_CLASS, MODE_MISTAKE_REDO, MODE_SEQUENTIAL, MODE_RANDOM, MODE_MISTAKES));
        filters.put("mistakeStatuses", List.of(STATUS_UNRESOLVED, STATUS_REVIEWING, STATUS_RESOLVED));
        return filters;
    }

    @Override
    public Map<String, Object> listQuestions(Map<String, Object> params) {
        int pageNo = parseInt(params.get("page"), 1);
        int pageSize = Math.min(100, Math.max(1, parseInt(params.get("pageSize"), 12)));
        QueryWrapper<PracticeQuestion> wrapper = new QueryWrapper<>();
        applyQuestionFilters(wrapper,
                str(params.get("subject")),
                str(params.get("gradeLevel")),
                str(params.get("track")),
                str(params.get("chapter")),
                str(params.get("knowledgePoint")),
                str(params.get("questionType")),
                str(params.get("difficulty")),
                str(params.get("lessonId")),
                false);
        if (StringUtils.hasText(str(params.get("status")))) {
            wrapper.eq("status", str(params.get("status")));
        } else {
            wrapper.ne("status", STATUS_ARCHIVED);
        }
        if (StringUtils.hasText(str(params.get("keyword")))) {
            String keyword = str(params.get("keyword"));
            wrapper.and(w -> w.like("title", keyword).or().like("content", keyword).or().like("knowledge_point", keyword));
        }
        wrapper.orderByDesc("created_at").orderByDesc("id");
        Page<PracticeQuestion> page = questionMapper.selectPage(new Page<>(pageNo, pageSize), wrapper);
        Map<String, Object> result = new HashMap<>();
        result.put("records", page.getRecords().stream().map(q -> toQuestionMap(q, true)).collect(Collectors.toList()));
        result.put("total", page.getTotal());
        result.put("page", page.getCurrent());
        result.put("pageSize", page.getSize());
        return result;
    }

    @Override
    public Map<String, Object> getQuestionDetail(Long questionId) {
        PracticeQuestion question = questionMapper.selectById(questionId);
        if (question == null) {
            throw new IllegalArgumentException("题目不存在");
        }
        return toQuestionMap(question, true);
    }

    @Override
    @Transactional
    public Map<String, Object> createQuestion(PracticeController.QuestionRequest request) {
        PracticeQuestion question = fromQuestionRequest(new PracticeQuestion(), request);
        question.setCreatedAt(LocalDateTime.now());
        questionMapper.insert(question);
        return toQuestionMap(question, true);
    }

    @Override
    @Transactional
    public Map<String, Object> updateQuestion(Long questionId, PracticeController.QuestionRequest request) {
        PracticeQuestion existing = questionMapper.selectById(questionId);
        if (existing == null) {
            throw new IllegalArgumentException("题目不存在");
        }
        PracticeQuestion update = fromQuestionRequest(existing, request);
        update.setId(questionId);
        questionMapper.updateById(update);
        return toQuestionMap(questionMapper.selectById(questionId), true);
    }

    @Override
    public void deleteQuestion(Long questionId) {
        PracticeQuestion existing = questionMapper.selectById(questionId);
        if (existing == null) {
            return;
        }
        long used = recordMapper.selectCount(new QueryWrapper<PracticeAnswerRecord>().eq("question_id", questionId));
        if (used > 0) {
            PracticeQuestion update = new PracticeQuestion();
            update.setId(questionId);
            update.setStatus(STATUS_ARCHIVED);
            questionMapper.updateById(update);
        } else {
            questionMapper.deleteById(questionId);
        }
    }

    @Override
    @Transactional
    public Map<String, Object> importQuestions(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("请上传题库文件，支持 " + PracticeQuestionImportParser.SUPPORTED_FORMATS);
        }
        List<PracticeQuestion> questions;
        try {
            questions = questionImportParser.parse(file);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("题库文件解析失败：" + e.getMessage(), e);
        }
        int inserted = 0;
        int pendingReview = 0;
        List<String> errors = new ArrayList<>();
        for (int i = 0; i < questions.size(); i++) {
            try {
                validateQuestion(questions.get(i));
                questionMapper.insert(questions.get(i));
                inserted++;
                if (STATUS_DISABLED.equals(questions.get(i).getStatus())) {
                    pendingReview++;
                }
            } catch (Exception e) {
                errors.add("第 " + (i + 1) + " 题导入失败：" + e.getMessage());
            }
        }
        Map<String, Object> result = new HashMap<>();
        result.put("filename", file.getOriginalFilename());
        result.put("supportedFormats", PracticeQuestionImportParser.SUPPORTED_FORMATS);
        result.put("totalRows", questions.size());
        result.put("inserted", inserted);
        result.put("pendingReview", pendingReview);
        result.put("failed", errors.size());
        result.put("errors", errors);
        return result;
    }

    @Override
    public Map<String, Object> getNextQuestion(Long userId, String mode, String track, String chapter, String knowledgePoint) {
        return getNextQuestion(userId, mode, null, null, track, chapter, knowledgePoint, null, null, null);
    }

    @Override
    public Map<String, Object> getNextQuestion(Long userId, String mode, String subject, String gradeLevel, String track, String chapter, String knowledgePoint, String questionType, String difficulty, String lessonId) {
        ensureStats(userId);
        String normalizedMode = normalizeMode(mode);
        PracticeQuestion question;

        if (MODE_MISTAKES.equals(normalizedMode) || MODE_MISTAKE_REDO.equals(normalizedMode)) {
            PracticeMistake mistake = mistakeMapper.selectOne(new QueryWrapper<PracticeMistake>()
                    .eq("user_id", userId)
                    .ne("status", STATUS_RESOLVED)
                    .orderByDesc("last_wrong_at")
                    .last("LIMIT 1"));
            question = mistake == null ? null : questionMapper.selectById(mistake.getQuestionId());
        } else {
            QueryWrapper<PracticeQuestion> wrapper = new QueryWrapper<>();
            applyQuestionFilters(wrapper, subject, gradeLevel, track, chapter, knowledgePoint, questionType, difficulty, lessonId, true);
            if (MODE_AFTER_CLASS.equals(normalizedMode) && !StringUtils.hasText(lessonId)) {
                wrapper.isNotNull("lesson_id");
            }
            excludeRecentQuestions(userId, wrapper, RECENT_DEDUP_DAYS);
            if (MODE_RANDOM.equals(normalizedMode) || MODE_FREE.equals(normalizedMode)) {
                wrapper.last("ORDER BY RAND() LIMIT 1");
                question = questionMapper.selectOne(wrapper);
            } else {
                question = selectSequentialQuestion(userId, wrapper);
            }

            if (question == null) {
                QueryWrapper<PracticeQuestion> fallback = new QueryWrapper<>();
                applyQuestionFilters(fallback, subject, gradeLevel, track, chapter, knowledgePoint, questionType, difficulty, lessonId, true);
                question = MODE_RANDOM.equals(normalizedMode) || MODE_FREE.equals(normalizedMode)
                        ? questionMapper.selectOne(fallback.last("ORDER BY RAND() LIMIT 1"))
                        : selectSequentialQuestion(userId, fallback);
            }
        }

        if (question == null) {
            question = questionMapper.selectOne(new QueryWrapper<PracticeQuestion>()
                    .eq("status", STATUS_ENABLED)
                    .orderByAsc("id")
                    .last("LIMIT 1"));
        }

        Map<String, Object> result = new HashMap<>();
        result.put("question", toQuestionMap(question, false));
        result.put("mode", normalizedMode);
        result.put("stats", getDashboard(userId));
        return result;
    }

    @Override
    @Transactional
    public Map<String, Object> submitAnswer(Long userId, PracticeController.SubmitAnswerRequest request) {
        if (request.getQuestionId() == null) {
            throw new IllegalArgumentException("questionId 不能为空");
        }
        if (!StringUtils.hasText(request.getUserAnswer())) {
            throw new IllegalArgumentException("请先填写或选择答案");
        }
        PracticeQuestion question = questionMapper.selectById(request.getQuestionId());
        if (question == null || !STATUS_ENABLED.equalsIgnoreCase(nvl(question.getStatus(), STATUS_ENABLED))) {
            throw new IllegalArgumentException("题目不存在或已停用");
        }

        PracticeUserStats stats = ensureStats(userId);
        int previousAttempts = Math.toIntExact(recordMapper.selectCount(new QueryWrapper<PracticeAnswerRecord>()
                .eq("user_id", userId)
                .eq("question_id", question.getId())));
        int attempt = previousAttempts + 1;
        JudgeResult judge = judgeAnswer(question, request.getUserAnswer());
        boolean hadWrongBefore = mistakeMapper.selectCount(new QueryWrapper<PracticeMistake>()
                .eq("user_id", userId)
                .eq("question_id", question.getId())) > 0;
        boolean conquered = judge.correct() && hadWrongBefore;
        int points = judge.correct() ? calculatePoints(question.getDifficulty(), attempt) : 0;

        PracticeAnswerRecord record = new PracticeAnswerRecord();
        record.setUserId(userId);
        record.setQuestionId(question.getId());
        record.setAnsweredAt(LocalDateTime.now());
        record.setDurationSeconds(request.getDurationSeconds() == null ? 0 : Math.max(0, request.getDurationSeconds()));
        record.setUserAnswer(request.getUserAnswer());
        record.setCorrectAnswer(question.getCorrectAnswer());
        record.setCorrect(judge.correct());
        record.setJudgeScore(judge.score());
        record.setJudgeFeedback(judge.feedback());
        record.setPoints(points);
        record.setChapter(question.getChapter());
        record.setKnowledgePoint(question.getKnowledgePoint());
        record.setDifficulty(question.getDifficulty());
        record.setTrack(question.getTrack());
        record.setQuestionType(question.getQuestionType());
        record.setAttemptNumber(attempt);
        record.setSourceMode(StringUtils.hasText(request.getMode()) ? normalizeMode(request.getMode()) : MODE_SEQUENTIAL);
        record.setDoubtful(false);
        record.setReviewNote("");
        recordMapper.insert(record);

        syncMistakeBook(userId, question, judge.correct());
        int dailyBonus = awardDailyBonusIfNeeded(userId, stats, points);
        refreshStatsAfterAnswer(userId, stats, judge.correct(), points + dailyBonus, conquered, question.getTrack());
        refreshRedisRank(userId);

        Map<String, Object> result = new HashMap<>();
        result.put("record", toRecordMap(record));
        result.put("question", toQuestionMap(question, true));
        result.put("correct", judge.correct());
        result.put("judgeScore", judge.score());
        result.put("judgeFeedback", judge.feedback());
        result.put("points", points);
        result.put("dailyBonus", dailyBonus);
        result.put("attemptNumber", attempt);
        result.put("conquered", conquered);
        result.put("surpassPercent", calculateSurpassPercent(userId, question.getTrack()));
        result.put("nextQuestion", getNextQuestion(userId, request.getMode(), question.getSubject(), question.getGradeLevel(), question.getTrack(), question.getChapter(), null, question.getQuestionType(), null, question.getLessonId()).get("question"));
        result.put("dashboard", getDashboard(userId));
        return result;
    }

    @Override
    public List<Map<String, Object>> getRecords(Long userId, String range, String chapter, String knowledgePoint, Boolean wrongOnly) {
        QueryWrapper<PracticeAnswerRecord> wrapper = new QueryWrapper<PracticeAnswerRecord>()
                .eq("user_id", userId)
                .orderByDesc("answered_at");
        applyRange(wrapper, range);
        if (StringUtils.hasText(chapter)) {
            wrapper.eq("chapter", chapter);
        }
        if (StringUtils.hasText(knowledgePoint)) {
            wrapper.eq("knowledge_point", knowledgePoint);
        }
        if (Boolean.TRUE.equals(wrongOnly)) {
            wrapper.eq("correct", false);
        }
        return recordMapper.selectList(wrapper).stream().map(this::toRecordMap).collect(Collectors.toList());
    }

    @Override
    public List<Map<String, Object>> getMistakes(Long userId, String status, String chapter, String knowledgePoint) {
        QueryWrapper<PracticeMistake> wrapper = new QueryWrapper<PracticeMistake>()
                .eq("user_id", userId)
                .orderByDesc("updated_at");
        if (StringUtils.hasText(status)) {
            wrapper.eq("status", status.toUpperCase(Locale.ROOT));
        }
        List<PracticeMistake> mistakes = mistakeMapper.selectList(wrapper);
        return mistakes.stream()
                .map(this::toMistakeMap)
                .filter(row -> !StringUtils.hasText(chapter) || chapter.equals(row.get("chapter")))
                .filter(row -> !StringUtils.hasText(knowledgePoint) || knowledgePoint.equals(row.get("knowledgePoint")))
                .collect(Collectors.toList());
    }

    @Override
    public Map<String, Object> setMistakeFocus(Long userId, Long mistakeId, Boolean focused) {
        PracticeMistake mistake = getUserMistake(userId, mistakeId);
        boolean nextFocused = focused == null ? !Boolean.TRUE.equals(mistake.getDoubtful()) : Boolean.TRUE.equals(focused);
        PracticeMistake update = new PracticeMistake();
        update.setDoubtful(nextFocused);
        update.setLastReviewAt(LocalDateTime.now());
        mistakeMapper.update(update, new UpdateWrapper<PracticeMistake>().eq("id", mistakeId).eq("user_id", userId));
        return toMistakeMap(getUserMistake(userId, mistakeId));
    }

    @Override
    public void deleteMistake(Long userId, Long mistakeId) {
        int deleted = mistakeMapper.delete(new QueryWrapper<PracticeMistake>()
                .eq("id", mistakeId)
                .eq("user_id", userId));
        if (deleted == 0) {
            throw new IllegalArgumentException("错题不存在");
        }
    }

    @Override
    public Map<String, Object> createMistakeRedoSession(Long userId, List<Long> mistakeIds) {
        QueryWrapper<PracticeMistake> wrapper = new QueryWrapper<PracticeMistake>()
                .eq("user_id", userId)
                .ne("status", STATUS_RESOLVED)
                .orderByDesc("updated_at");
        if (mistakeIds != null && !mistakeIds.isEmpty()) {
            wrapper.in("id", mistakeIds);
        }
        List<PracticeMistake> mistakes = mistakeMapper.selectList(wrapper);
        List<Map<String, Object>> questions = mistakes.stream()
                .map(PracticeMistake::getQuestionId)
                .distinct()
                .map(questionMapper::selectById)
                .filter(Objects::nonNull)
                .map(question -> toQuestionMap(question, false))
                .collect(Collectors.toList());

        Map<String, Object> session = new HashMap<>();
        session.put("sessionId", "mistake_redo_" + System.currentTimeMillis());
        session.put("mode", MODE_MISTAKE_REDO);
        session.put("totalCount", questions.size());
        session.put("questions", questions);
        session.put("mistakeIds", mistakes.stream().map(PracticeMistake::getId).collect(Collectors.toList()));
        return session;
    }

    @Override
    public void updateMistakeStatus(Long userId, Long mistakeId, PracticeController.UpdateMistakeRequest request) {
        PracticeMistake mistake = mistakeMapper.selectOne(new QueryWrapper<PracticeMistake>()
                .eq("id", mistakeId)
                .eq("user_id", userId));
        if (mistake == null) {
            throw new IllegalArgumentException("错题不存在");
        }
        PracticeMistake update = new PracticeMistake();
        if (StringUtils.hasText(request.getStatus())) {
            String status = request.getStatus().toUpperCase(Locale.ROOT);
            if (!List.of(STATUS_UNRESOLVED, STATUS_REVIEWING, STATUS_RESOLVED).contains(status)) {
                throw new IllegalArgumentException("错题状态不合法");
            }
            update.setStatus(status);
            update.setLastReviewAt(LocalDateTime.now());
            if (STATUS_RESOLVED.equals(status)) {
                update.setResolvedAt(LocalDateTime.now());
            }
        }
        update.setDoubtful(request.getDoubtful());
        update.setReviewNote(request.getReviewNote());
        mistakeMapper.update(update, new UpdateWrapper<PracticeMistake>().eq("id", mistakeId).eq("user_id", userId));
    }

    private PracticeMistake getUserMistake(Long userId, Long mistakeId) {
        PracticeMistake mistake = mistakeMapper.selectOne(new QueryWrapper<PracticeMistake>()
                .eq("id", mistakeId)
                .eq("user_id", userId));
        if (mistake == null) {
            throw new IllegalArgumentException("错题不存在");
        }
        return mistake;
    }

    @Override
    public Map<String, Object> getDashboard(Long userId) {
        PracticeUserStats stats = ensureStats(userId);
        Map<String, Object> statistics = getStatistics(userId, "week");
        Map<String, Object> dashboard = new HashMap<>();
        dashboard.put("totalPoints", nvl(stats.getTotalPoints()));
        dashboard.put("totalAnswers", nvl(stats.getTotalAnswers()));
        dashboard.put("correctAnswers", nvl(stats.getCorrectAnswers()));
        dashboard.put("accuracy", stats.getTotalAnswers() == null || stats.getTotalAnswers() == 0 ? 0 :
                Math.round(stats.getCorrectAnswers() * 1000.0 / stats.getTotalAnswers()) / 10.0);
        dashboard.put("currentStreak", effectiveCurrentStreak(stats));
        dashboard.put("conqueredMistakes", nvl(stats.getConqueredMistakes()));
        dashboard.put("leaderboardHidden", Boolean.TRUE.equals(stats.getLeaderboardHidden()));
        dashboard.put("preferredTrack", stats.getPreferredTrack());
        dashboard.put("rankTitle", rankTitle(nvl(stats.getTotalPoints())));
        dashboard.put("dailyTarget", DAILY_TARGET);
        dashboard.put("todayCount", todayAnswerCount(userId));
        dashboard.put("weekly", statistics.get("trend"));
        dashboard.put("mistakeCount", mistakeMapper.selectCount(new QueryWrapper<PracticeMistake>()
                .eq("user_id", userId).ne("status", STATUS_RESOLVED)));
        dashboard.put("checkin", getCheckinStatus(userId));
        return dashboard;
    }

    @Override
    public Map<String, Object> getStatistics(Long userId, String range) {
        String normalizedRange = StringUtils.hasText(range) ? range.toLowerCase(Locale.ROOT) : "week";
        QueryWrapper<PracticeAnswerRecord> wrapper = new QueryWrapper<PracticeAnswerRecord>()
                .eq("user_id", userId);
        applyRange(wrapper, normalizedRange);
        List<PracticeAnswerRecord> records = recordMapper.selectList(wrapper.orderByAsc("answered_at"));
        long total = records.size();
        long correct = records.stream().filter(record -> Boolean.TRUE.equals(record.getCorrect())).count();
        int totalSeconds = records.stream().map(PracticeAnswerRecord::getDurationSeconds).filter(Objects::nonNull).mapToInt(Integer::intValue).sum();
        double avgSeconds = total == 0 ? 0 : Math.round(totalSeconds * 10.0 / total) / 10.0;

        Map<String, List<PracticeAnswerRecord>> byKnowledge = records.stream().collect(Collectors.groupingBy(
                record -> normalizedGroupName(record.getKnowledgePoint(), "未分类"), LinkedHashMap::new, Collectors.toList()));
        List<Map<String, Object>> knowledgeDistribution = byKnowledge.entrySet().stream()
                .map(entry -> knowledgeStat(entry.getKey(), entry.getValue()))
                .sorted((a, b) -> {
                    int masteryCompare = Double.compare(((Number) a.get("masteryScore")).doubleValue(), ((Number) b.get("masteryScore")).doubleValue());
                    return masteryCompare != 0 ? masteryCompare : Long.compare(((Number) b.get("count")).longValue(), ((Number) a.get("count")).longValue());
                })
                .collect(Collectors.toList());

        List<Map<String, Object>> chapterDistribution = groupStatistics(records, record -> normalizedGroupName(record.getChapter(), "未分类章节"));
        List<Map<String, Object>> questionTypeDistribution = groupStatistics(records, record -> normalizedGroupName(record.getQuestionType(), "UNKNOWN"));
        List<Map<String, Object>> difficultyDistribution = groupStatistics(records, record -> normalizedGroupName(record.getDifficulty(), "UNKNOWN"));

        List<Map<String, Object>> trend = new ArrayList<>();
        int days = switch (normalizedRange) {
            case "today" -> 1;
            case "month" -> 30;
            case "all" -> 14;
            default -> 7;
        };
        for (int i = days - 1; i >= 0; i--) {
            LocalDate day = LocalDate.now().minusDays(i);
            List<PracticeAnswerRecord> dayRecords = records.stream()
                    .filter(record -> record.getAnsweredAt() != null && record.getAnsweredAt().toLocalDate().equals(day))
                    .collect(Collectors.toList());
            long dayCorrect = dayRecords.stream().filter(record -> Boolean.TRUE.equals(record.getCorrect())).count();
            int daySeconds = dayRecords.stream().map(PracticeAnswerRecord::getDurationSeconds).filter(Objects::nonNull).mapToInt(Integer::intValue).sum();
            Map<String, Object> item = new HashMap<>();
            item.put("date", day.toString());
            item.put("count", dayRecords.size());
            item.put("accuracy", dayRecords.isEmpty() ? 0 : Math.round(dayCorrect * 1000.0 / dayRecords.size()) / 10.0);
            item.put("durationSeconds", daySeconds);
            item.put("averageDurationSeconds", dayRecords.isEmpty() ? 0 : Math.round(daySeconds * 10.0 / dayRecords.size()) / 10.0);
            trend.add(item);
        }

        List<PracticeAnswerRecord> previousRecords = previousPeriodRecords(userId, normalizedRange);
        double currentAccuracy = accuracyPercent(records);
        double previousAccuracy = accuracyPercent(previousRecords);
        boolean hasPreviousPeriod = !previousRecords.isEmpty();
        double accuracyChange = hasPreviousPeriod ? Math.round((currentAccuracy - previousAccuracy) * 10.0) / 10.0 : 0;
        long activeDays = records.stream()
                .map(PracticeAnswerRecord::getAnsweredAt)
                .filter(Objects::nonNull)
                .map(LocalDateTime::toLocalDate)
                .distinct()
                .count();
        PracticeUserStats userStats = ensureStats(userId);
        List<Map<String, Object>> wrongReasonDistribution = wrongReasonDistribution(records, avgSeconds);
        List<String> recommendations = buildRecommendations(records, knowledgeDistribution, questionTypeDistribution, currentAccuracy, accuracyChange, avgSeconds);

        Map<String, Object> result = new HashMap<>();
        result.put("range", normalizedRange);
        result.put("totalAnswers", total);
        result.put("correctAnswers", correct);
        result.put("incorrectAnswers", total - correct);
        result.put("accuracy", currentAccuracy);
        result.put("previousAccuracy", previousAccuracy);
        result.put("accuracyChange", accuracyChange);
        result.put("hasPreviousPeriod", hasPreviousPeriod);
        result.put("totalDurationSeconds", totalSeconds);
        result.put("averageDurationSeconds", avgSeconds);
        result.put("activeDays", activeDays);
        result.put("bestStreak", bestStreak(records));
        result.put("currentStreak", effectiveCurrentStreak(userStats));
        result.put("knowledgeDistribution", knowledgeDistribution);
        result.put("chapterDistribution", chapterDistribution);
        result.put("questionTypeDistribution", questionTypeDistribution);
        result.put("difficultyDistribution", difficultyDistribution);
        result.put("wrongReasonDistribution", wrongReasonDistribution);
        result.put("trend", trend);
        result.put("weakKnowledgePoints", knowledgeDistribution.stream()
                .filter(item -> Boolean.TRUE.equals(item.get("needsReview")))
                .limit(5)
                .collect(Collectors.toList()));
        result.put("strongKnowledgePoints", knowledgeDistribution.stream()
                .filter(item -> ((Number) item.get("count")).longValue() >= 3)
                .filter(item -> ((Number) item.get("masteryScore")).doubleValue() >= 80)
                .sorted((a, b) -> Double.compare(((Number) b.get("masteryScore")).doubleValue(), ((Number) a.get("masteryScore")).doubleValue()))
                .limit(5)
                .collect(Collectors.toList()));
        result.put("recommendations", recommendations);
        return result;
    }

    @Override
    public Map<String, Object> getLeaderboards(Long userId, String track) {
        PracticeUserStats self = ensureStats(userId);
        String effectiveTrack = StringUtils.hasText(track) ? track : self.getPreferredTrack();
        Map<String, Object> result = new HashMap<>();
        result.put("track", effectiveTrack);
        result.put("hidden", Boolean.TRUE.equals(self.getLeaderboardHidden()));
        List<Map<String, Object>> redisRows = redisRankRows(effectiveTrack);
        result.put("trackRanking", redisRows.isEmpty() ? mysqlRankRows(effectiveTrack) : redisRows);
        result.put("teamRanking", List.of());
        result.put("teamHint", "小队表与成员表已建立，可基于 inviteCode 扩展 3-8 人封闭小队。");
        result.put("rankStorage", redisRows.isEmpty() ? "MYSQL_FALLBACK" : "REDIS_ZSET");
        return result;
    }

    @Override
    @Transactional
    public Map<String, Object> checkin(Long userId) {
        ensureStats(userId);
        LocalDate today = LocalDate.now();
        PracticeCheckin exists = checkinMapper.selectOne(new QueryWrapper<PracticeCheckin>()
                .eq("user_id", userId)
                .eq("checkin_date", today));
        Map<String, Object> result = new HashMap<>();
        if (exists != null) {
            result.put("checked", true);
            result.put("alreadyChecked", true);
            result.put("points", 0);
            result.put("status", getCheckinStatus(userId));
            return result;
        }
        PracticeCheckin checkin = new PracticeCheckin();
        checkin.setUserId(userId);
        checkin.setCheckinDate(today);
        checkin.setPoints(CHECKIN_POINTS);
        checkinMapper.insert(checkin);

        PracticeUserStats stats = ensureStats(userId);
        PracticeUserStats update = new PracticeUserStats();
        update.setTotalPoints(nvl(stats.getTotalPoints()) + CHECKIN_POINTS);
        statsMapper.update(update, new UpdateWrapper<PracticeUserStats>().eq("user_id", userId));
        refreshRedisRank(userId);

        result.put("checked", true);
        result.put("alreadyChecked", false);
        result.put("points", CHECKIN_POINTS);
        result.put("status", getCheckinStatus(userId));
        return result;
    }

    @Override
    public Map<String, Object> getCheckinStatus(Long userId) {
        LocalDate today = LocalDate.now();
        PracticeUserStats stats = ensureStats(userId);
        boolean checkedToday = checkinMapper.selectCount(new QueryWrapper<PracticeCheckin>()
                .eq("user_id", userId)
                .eq("checkin_date", today)) > 0;
        long monthly = checkinMapper.selectCount(new QueryWrapper<PracticeCheckin>()
                .eq("user_id", userId)
                .ge("checkin_date", today.withDayOfMonth(1)));
        Map<String, Object> result = new HashMap<>();
        result.put("checkedToday", checkedToday);
        result.put("checkinPoints", CHECKIN_POINTS);
        result.put("monthCheckins", monthly);
        result.put("streakDays", effectiveCurrentStreak(stats));
        result.put("totalPoints", nvl(stats.getTotalPoints()));
        return result;
    }

    @Override
    public void updatePrivacy(Long userId, boolean hidden) {
        ensureStats(userId);
        PracticeUserStats stats = new PracticeUserStats();
        stats.setLeaderboardHidden(hidden);
        statsMapper.update(stats, new UpdateWrapper<PracticeUserStats>().eq("user_id", userId));
    }

    @Override
    public void updateMistake(Long userId, Long recordId, PracticeController.UpdateMistakeRequest request) {
        PracticeAnswerRecord record = recordMapper.selectOne(new QueryWrapper<PracticeAnswerRecord>()
                .eq("id", recordId)
                .eq("user_id", userId));
        if (record == null) {
            throw new IllegalArgumentException("记录不存在");
        }
        PracticeAnswerRecord update = new PracticeAnswerRecord();
        update.setDoubtful(request.getDoubtful());
        update.setReviewNote(request.getReviewNote());
        recordMapper.update(update, new UpdateWrapper<PracticeAnswerRecord>().eq("id", recordId).eq("user_id", userId));

        PracticeMistake mistake = mistakeMapper.selectOne(new QueryWrapper<PracticeMistake>()
                .eq("user_id", userId)
                .eq("question_id", record.getQuestionId()));
        if (mistake != null) {
            PracticeController.UpdateMistakeRequest mistakeRequest = new PracticeController.UpdateMistakeRequest();
            mistakeRequest.setDoubtful(request.getDoubtful());
            mistakeRequest.setReviewNote(request.getReviewNote());
            mistakeRequest.setStatus(request.getStatus());
            updateMistakeStatus(userId, mistake.getId(), mistakeRequest);
        }
    }

    @Override
    public String exportRecords(Long userId, boolean wrongOnly) {
        List<Map<String, Object>> rows = getRecords(userId, "all", null, null, wrongOnly);
        StringBuilder builder = new StringBuilder();
        builder.append(wrongOnly ? "错题本导出\n" : "答题记录导出\n");
        builder.append("导出时间: ").append(LocalDateTime.now()).append("\n\n");
        for (Map<String, Object> row : rows) {
            builder.append("题目: ").append(row.get("questionTitle")).append("\n")
                    .append("章节/知识点: ").append(row.get("chapter")).append(" / ").append(row.get("knowledgePoint")).append("\n")
                    .append("题型/难度: ").append(row.get("questionType")).append(" / ").append(row.get("difficulty")).append("\n")
                    .append("作答: ").append(row.get("userAnswer")).append(" | 标准答案: ").append(row.get("correctAnswer"))
                    .append(" | 正误: ").append(Boolean.TRUE.equals(row.get("correct")) ? "正确" : "错误").append("\n")
                    .append("评分: ").append(row.get("judgeScore")).append(" | 反馈: ").append(row.get("judgeFeedback")).append("\n")
                    .append("积分: ").append(row.get("points")).append(" | 用时: ").append(row.get("durationSeconds")).append("秒 | 时间: ").append(row.get("answeredAt")).append("\n")
                    .append("复习笔记: ").append(row.getOrDefault("reviewNote", "")).append("\n\n");
        }
        return builder.toString();
    }

    @Override
    @Transactional
    public void clearUserData(Long userId) {
        mistakeMapper.delete(new QueryWrapper<PracticeMistake>().eq("user_id", userId));
        recordMapper.delete(new QueryWrapper<PracticeAnswerRecord>().eq("user_id", userId));
        checkinMapper.delete(new QueryWrapper<PracticeCheckin>().eq("user_id", userId));
        statsMapper.delete(new QueryWrapper<PracticeUserStats>().eq("user_id", userId));
        ensureStats(userId);
        refreshRedisRank(userId);
    }

    private List<Object> distinctColumn(String column) {
        return questionMapper.selectObjs(new QueryWrapper<PracticeQuestion>()
                .select("DISTINCT " + column)
                .isNotNull(column)
                .ne(column, "")
                .eq("status", STATUS_ENABLED)
                .orderByAsc(column));
    }

    private PracticeQuestion fromQuestionRequest(PracticeQuestion question, PracticeController.QuestionRequest request) {
        question.setSubject(nvl(request.getSubject(), "通用"));
        question.setGradeLevel(nvl(request.getGradeLevel(), "大学"));
        question.setQuestionType(nvl(request.getQuestionType(), "SINGLE_CHOICE").toUpperCase(Locale.ROOT));
        question.setTitle(required(request.getTitle(), "题目标题不能为空"));
        question.setContent(required(request.getContent(), "题干不能为空"));
        question.setOptionA(request.getOptionA());
        question.setOptionB(request.getOptionB());
        question.setOptionC(request.getOptionC());
        question.setOptionD(request.getOptionD());
        question.setCorrectAnswer(required(request.getCorrectAnswer(), "标准答案不能为空"));
        question.setAnswerKeywords(request.getAnswerKeywords());
        question.setAnalysis(nvl(request.getAnalysis(), ""));
        question.setChapter(required(request.getChapter(), "章节不能为空"));
        question.setKnowledgePoint(required(request.getKnowledgePoint(), "知识点不能为空"));
        question.setDifficulty(nvl(request.getDifficulty(), "BASIC").toUpperCase(Locale.ROOT));
        question.setTrack(nvl(request.getTrack(), question.getSubject()));
        question.setLessonId(request.getLessonId());
        question.setStatus(nvl(request.getStatus(), STATUS_ENABLED).toUpperCase(Locale.ROOT));
        validateQuestion(question);
        return question;
    }

    private void validateQuestion(PracticeQuestion question) {
        if (!List.of("SINGLE_CHOICE", "MULTIPLE_CHOICE", "FILL_BLANK", "SHORT_ANSWER").contains(question.getQuestionType())) {
            throw new IllegalArgumentException("题型不合法");
        }
        if (!List.of("BASIC", "ADVANCED", "HARD").contains(question.getDifficulty())) {
            throw new IllegalArgumentException("难度不合法");
        }
        if (!List.of(STATUS_ENABLED, STATUS_DISABLED).contains(question.getStatus())) {
            throw new IllegalArgumentException("题目状态不合法");
        }
        if (("SINGLE_CHOICE".equals(question.getQuestionType()) || "MULTIPLE_CHOICE".equals(question.getQuestionType()))
                && (!StringUtils.hasText(question.getOptionA()) || !StringUtils.hasText(question.getOptionB()))) {
            throw new IllegalArgumentException("选择题至少需要 A、B 两个选项");
        }
    }

    private void applyQuestionFilters(QueryWrapper<PracticeQuestion> wrapper, String subject, String gradeLevel, String track, String chapter, String knowledgePoint, String questionType, String difficulty, String lessonId, boolean onlyEnabled) {
        if (onlyEnabled) {
            wrapper.eq("status", STATUS_ENABLED);
        }
        if (StringUtils.hasText(subject)) {
            wrapper.eq("subject", subject);
        }
        if (StringUtils.hasText(gradeLevel)) {
            wrapper.eq("grade_level", gradeLevel);
        }
        if (StringUtils.hasText(track)) {
            wrapper.eq("track", track);
        }
        if (StringUtils.hasText(chapter)) {
            wrapper.eq("chapter", chapter);
        }
        if (StringUtils.hasText(knowledgePoint)) {
            wrapper.eq("knowledge_point", knowledgePoint);
        }
        if (StringUtils.hasText(questionType)) {
            wrapper.eq("question_type", questionType);
        }
        if (StringUtils.hasText(difficulty)) {
            wrapper.eq("difficulty", difficulty);
        }
        if (StringUtils.hasText(lessonId)) {
            wrapper.eq("lesson_id", lessonId);
        }
    }

    private void excludeRecentQuestions(Long userId, QueryWrapper<PracticeQuestion> wrapper, int days) {
        List<Long> recentIds = recordMapper.selectList(new QueryWrapper<PracticeAnswerRecord>()
                .eq("user_id", userId)
                .ge("answered_at", LocalDateTime.now().minusDays(days))
                .select("question_id"))
                .stream()
                .map(PracticeAnswerRecord::getQuestionId)
                .distinct()
                .collect(Collectors.toList());
        if (!recentIds.isEmpty()) {
            wrapper.notIn("id", recentIds);
        }
    }

    private PracticeQuestion selectSequentialQuestion(Long userId, QueryWrapper<PracticeQuestion> wrapper) {
        List<PracticeQuestion> questions = questionMapper.selectList(wrapper.orderByAsc("id"));
        if (questions.isEmpty()) {
            return null;
        }
        return questions.get(0);
    }

    private JudgeResult judgeAnswer(PracticeQuestion question, String userAnswer) {
        String type = nvl(question.getQuestionType(), "SINGLE_CHOICE").toUpperCase(Locale.ROOT);
        return switch (type) {
            case "MULTIPLE_CHOICE" -> judgeMultipleChoice(question, userAnswer);
            case "FILL_BLANK" -> judgeFillBlank(question, userAnswer);
            case "SHORT_ANSWER" -> judgeShortAnswer(question, userAnswer);
            default -> judgeSingleChoice(question, userAnswer);
        };
    }

    private JudgeResult judgeSingleChoice(PracticeQuestion question, String userAnswer) {
        boolean correct = normalizeChoice(question.getCorrectAnswer()).equals(normalizeChoice(userAnswer));
        return new JudgeResult(correct, correct ? 100.0 : 0.0, correct ? "选择题精确匹配正确" : "选择题答案不匹配");
    }

    private JudgeResult judgeMultipleChoice(PracticeQuestion question, String userAnswer) {
        boolean correct = normalizeChoiceSet(question.getCorrectAnswer()).equals(normalizeChoiceSet(userAnswer));
        return new JudgeResult(correct, correct ? 100.0 : 0.0, correct ? "多选题选项集合匹配正确" : "多选题选项集合不匹配");
    }

    private JudgeResult judgeFillBlank(PracticeQuestion question, String userAnswer) {
        String normalizedUser = normalizeText(userAnswer);
        List<String> answers = answerCandidates(question);
        boolean exact = answers.stream().map(this::normalizeText).anyMatch(normalizedUser::equals);
        if (exact) {
            return new JudgeResult(true, 100.0, "填空题答案匹配");
        }
        boolean numericNear = answers.stream().anyMatch(answer -> numericEquals(answer, userAnswer));
        if (numericNear) {
            return new JudgeResult(true, 95.0, "填空题数值等价");
        }
        boolean fuzzy = answers.stream().map(this::normalizeText).anyMatch(answer -> similarity(answer, normalizedUser) >= 0.82);
        return new JudgeResult(fuzzy, fuzzy ? 85.0 : 0.0, fuzzy ? "填空题模糊匹配通过" : "填空题答案未达到模糊匹配阈值");
    }

    private JudgeResult judgeShortAnswer(PracticeQuestion question, String userAnswer) {
        List<String> keywords = answerCandidates(question);
        String normalizedUser = normalizeText(userAnswer);
        long hit = keywords.stream()
                .map(this::normalizeText)
                .filter(StringUtils::hasText)
                .filter(normalizedUser::contains)
                .count();
        double keywordScore = keywords.isEmpty() ? similarity(normalizeText(question.getCorrectAnswer()), normalizedUser) : hit * 1.0 / keywords.size();
        boolean correct = keywordScore >= 0.6;
        String feedback = correct
                ? "简答题关键词覆盖较好，语义判断通过"
                : "简答题关键词覆盖不足，建议补充核心概念";

        AIModelService ai = aiModelServiceProvider.getIfAvailable();
        if (ai != null) {
            try {
                String prompt = "请判断学生简答是否表达了标准答案的核心含义，只返回一句简短中文反馈。标准答案："
                        + question.getCorrectAnswer() + "；关键词：" + question.getAnswerKeywords()
                        + "；学生答案：" + userAnswer;
                String aiFeedback = ai.getAIResponseNoPrompt(prompt).block(Duration.ofSeconds(4));
                if (StringUtils.hasText(aiFeedback) && !aiFeedback.contains("暂时不可用")) {
                    feedback = "AI语义反馈：" + aiFeedback;
                }
            } catch (Exception ignored) {
                feedback += "；AI语义服务暂不可用，已使用本地语义关键词策略";
            }
        }
        return new JudgeResult(correct, Math.round(keywordScore * 1000.0) / 10.0, feedback);
    }

    private void syncMistakeBook(Long userId, PracticeQuestion question, boolean correct) {
        PracticeMistake mistake = mistakeMapper.selectOne(new QueryWrapper<PracticeMistake>()
                .eq("user_id", userId)
                .eq("question_id", question.getId()));
        if (!correct) {
            if (mistake == null) {
                mistake = new PracticeMistake();
                mistake.setUserId(userId);
                mistake.setQuestionId(question.getId());
                mistake.setStatus(STATUS_UNRESOLVED);
                mistake.setWrongCount(1);
                mistake.setReviewCount(0);
                mistake.setDoubtful(false);
                mistake.setLastWrongAt(LocalDateTime.now());
                mistakeMapper.insert(mistake);
            } else {
                PracticeMistake update = new PracticeMistake();
                update.setWrongCount(nvl(mistake.getWrongCount()) + 1);
                update.setStatus(STATUS_UNRESOLVED);
                update.setLastWrongAt(LocalDateTime.now());
                mistakeMapper.update(update, new UpdateWrapper<PracticeMistake>().eq("id", mistake.getId()));
            }
        } else if (mistake != null) {
            PracticeMistake update = new PracticeMistake();
            update.setStatus(STATUS_RESOLVED);
            update.setReviewCount(nvl(mistake.getReviewCount()) + 1);
            update.setLastReviewAt(LocalDateTime.now());
            update.setResolvedAt(LocalDateTime.now());
            mistakeMapper.update(update, new UpdateWrapper<PracticeMistake>().eq("id", mistake.getId()));
        }
    }

    private PracticeUserStats ensureStats(Long userId) {
        PracticeUserStats stats = statsMapper.selectById(userId);
        if (stats == null) {
            stats = new PracticeUserStats();
            stats.setUserId(userId);
            stats.setTotalPoints(0);
            stats.setTotalAnswers(0);
            stats.setCorrectAnswers(0);
            stats.setConqueredMistakes(0);
            stats.setCurrentStreak(0);
            stats.setLeaderboardHidden(false);
            stats.setPreferredTrack("高数期末");
            statsMapper.insert(stats);
        }
        return stats;
    }

    private int calculatePoints(String difficulty, int attempt) {
        int base = switch (nvl(difficulty, "BASIC")) {
            case "HARD" -> 15;
            case "ADVANCED" -> 10;
            default -> 5;
        };
        if (attempt == 1) {
            return base;
        }
        if (attempt == 2) {
            return Math.max(1, base / 2);
        }
        return 0;
    }

    private int awardDailyBonusIfNeeded(Long userId, PracticeUserStats stats, int answerPoints) {
        LocalDate today = LocalDate.now();
        if (today.equals(stats.getDailyBonusDate())) {
            return 0;
        }
        long todayCount = todayAnswerCount(userId);
        return todayCount >= DAILY_TARGET && answerPoints > 0 ? DAILY_BONUS : 0;
    }

    private void refreshStatsAfterAnswer(Long userId, PracticeUserStats stats, boolean correct, int points, boolean conquered, String track) {
        LocalDate today = LocalDate.now();
        Integer streak = nvl(stats.getCurrentStreak());
        if (stats.getLastPracticeDate() == null) {
            streak = 1;
        } else if (stats.getLastPracticeDate().plusDays(1).equals(today)) {
            streak += 1;
        } else if (!stats.getLastPracticeDate().equals(today)) {
            streak = 1;
        }

        PracticeUserStats update = new PracticeUserStats();
        update.setTotalPoints(nvl(stats.getTotalPoints()) + points);
        update.setTotalAnswers(nvl(stats.getTotalAnswers()) + 1);
        update.setCorrectAnswers(nvl(stats.getCorrectAnswers()) + (correct ? 1 : 0));
        update.setConqueredMistakes(nvl(stats.getConqueredMistakes()) + (conquered ? 1 : 0));
        update.setCurrentStreak(streak);
        update.setLastPracticeDate(today);
        update.setPreferredTrack(track);
        if (todayAnswerCount(userId) >= DAILY_TARGET && !today.equals(stats.getDailyBonusDate())) {
            update.setDailyBonusDate(today);
        }
        statsMapper.update(update, new UpdateWrapper<PracticeUserStats>().eq("user_id", userId));
    }

    private long todayAnswerCount(Long userId) {
        return recordMapper.selectCount(new QueryWrapper<PracticeAnswerRecord>()
                .eq("user_id", userId)
                .ge("answered_at", LocalDate.now().atStartOfDay())
                .le("answered_at", LocalDateTime.of(LocalDate.now(), LocalTime.MAX)));
    }

    private int effectiveCurrentStreak(PracticeUserStats stats) {
        if (stats == null || stats.getLastPracticeDate() == null) {
            return 0;
        }
        LocalDate yesterday = LocalDate.now().minusDays(1);
        if (stats.getLastPracticeDate().isBefore(yesterday)) {
            return 0;
        }
        return nvl(stats.getCurrentStreak());
    }

    private void applyRange(QueryWrapper<PracticeAnswerRecord> wrapper, String range) {
        if ("today".equalsIgnoreCase(range)) {
            wrapper.ge("answered_at", LocalDate.now().atStartOfDay());
        } else if ("week".equalsIgnoreCase(range)) {
            wrapper.ge("answered_at", LocalDate.now().minusDays(6).atStartOfDay());
        } else if ("month".equalsIgnoreCase(range)) {
            wrapper.ge("answered_at", LocalDate.now().minusDays(29).atStartOfDay());
        }
    }

    private List<Map<String, Object>> mysqlRankRows(String track) {
        List<PracticeUserStats> stats = statsMapper.selectList(new QueryWrapper<PracticeUserStats>()
                .eq("leaderboard_hidden", false)
                .eq(StringUtils.hasText(track), "preferred_track", track)
                .orderByDesc("total_points")
                .last("LIMIT 20"));
        return toRankRows(stats);
    }

    private List<Map<String, Object>> redisRankRows(String track) {
        StringRedisTemplate redis = redisTemplateProvider.getIfAvailable();
        if (redis == null || !StringUtils.hasText(track)) {
            return List.of();
        }
        try {
            Set<ZSetOperations.TypedTuple<String>> rows = redis.opsForZSet().reverseRangeWithScores(redisRankKey(track), 0, 19);
            if (rows == null || rows.isEmpty()) {
                seedRedisRank(track);
                rows = redis.opsForZSet().reverseRangeWithScores(redisRankKey(track), 0, 19);
            }
            if (rows == null) {
                return List.of();
            }
            List<Map<String, Object>> result = new ArrayList<>();
            int rank = 1;
            for (ZSetOperations.TypedTuple<String> tuple : rows) {
                Long uid = Long.valueOf(tuple.getValue());
                PracticeUserStats stats = statsMapper.selectById(uid);
                if (stats == null || Boolean.TRUE.equals(stats.getLeaderboardHidden())) {
                    continue;
                }
                User user = userMapper.selectById(uid);
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("rank", rank++);
                row.put("userId", uid);
                row.put("username", user == null ? "用户" + uid : user.getUsername());
                row.put("studentName", user == null ? "" : user.getStudentName());
                row.put("totalPoints", tuple.getScore() == null ? nvl(stats.getTotalPoints()) : tuple.getScore().intValue());
                row.put("streakDays", effectiveCurrentStreak(stats));
                row.put("rankTitle", rankTitle(nvl(stats.getTotalPoints())));
                result.add(row);
            }
            return result;
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private void seedRedisRank(String track) {
        StringRedisTemplate redis = redisTemplateProvider.getIfAvailable();
        if (redis == null) {
            return;
        }
        for (PracticeUserStats item : statsMapper.selectList(new QueryWrapper<PracticeUserStats>()
                .eq("leaderboard_hidden", false)
                .eq("preferred_track", track))) {
            redis.opsForZSet().add(redisRankKey(track), String.valueOf(item.getUserId()), nvl(item.getTotalPoints()));
        }
    }

    private void refreshRedisRank(Long userId) {
        StringRedisTemplate redis = redisTemplateProvider.getIfAvailable();
        if (redis == null) {
            return;
        }
        try {
            PracticeUserStats stats = statsMapper.selectById(userId);
            if (stats == null || !StringUtils.hasText(stats.getPreferredTrack())) {
                return;
            }
            if (Boolean.TRUE.equals(stats.getLeaderboardHidden())) {
                redis.opsForZSet().remove(redisRankKey(stats.getPreferredTrack()), String.valueOf(userId));
            } else {
                redis.opsForZSet().add(redisRankKey(stats.getPreferredTrack()), String.valueOf(userId), nvl(stats.getTotalPoints()));
            }
        } catch (Exception ignored) {
            // Redis unavailable should not block normal practice flow; MySQL leaderboard remains as fallback.
        }
    }

    private String redisRankKey(String track) {
        return "practice:rank:track:" + track;
    }

    private List<Map<String, Object>> toRankRows(List<PracticeUserStats> stats) {
        List<Map<String, Object>> rows = new ArrayList<>();
        int rank = 1;
        for (PracticeUserStats item : stats) {
            User user = userMapper.selectById(item.getUserId());
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("rank", rank++);
            row.put("userId", item.getUserId());
            row.put("username", user == null ? "用户" + item.getUserId() : user.getUsername());
            row.put("studentName", user == null ? "" : user.getStudentName());
            row.put("totalPoints", nvl(item.getTotalPoints()));
            row.put("streakDays", effectiveCurrentStreak(item));
            row.put("rankTitle", rankTitle(nvl(item.getTotalPoints())));
            rows.add(row);
        }
        return rows;
    }

    private double calculateSurpassPercent(Long userId, String track) {
        PracticeUserStats self = ensureStats(userId);
        List<PracticeUserStats> all = statsMapper.selectList(new QueryWrapper<PracticeUserStats>()
                .eq("leaderboard_hidden", false)
                .eq("preferred_track", track));
        if (all.size() <= 1) {
            return 100;
        }
        long lower = all.stream().filter(s -> nvl(s.getTotalPoints()) < nvl(self.getTotalPoints())).count();
        return Math.round(lower * 1000.0 / all.size()) / 10.0;
    }

    private Map<String, Object> toQuestionMap(PracticeQuestion question, boolean includeAnswer) {
        Map<String, Object> map = new HashMap<>();
        if (question == null) {
            return map;
        }
        map.put("id", question.getId());
        map.put("subject", question.getSubject());
        map.put("gradeLevel", question.getGradeLevel());
        map.put("questionType", question.getQuestionType());
        map.put("title", question.getTitle());
        map.put("content", question.getContent());
        Map<String, String> options = new LinkedHashMap<>();
        if (StringUtils.hasText(question.getOptionA())) options.put("A", question.getOptionA());
        if (StringUtils.hasText(question.getOptionB())) options.put("B", question.getOptionB());
        if (StringUtils.hasText(question.getOptionC())) options.put("C", question.getOptionC());
        if (StringUtils.hasText(question.getOptionD())) options.put("D", question.getOptionD());
        map.put("options", options);
        map.put("analysis", question.getAnalysis());
        map.put("chapter", question.getChapter());
        map.put("knowledgePoint", question.getKnowledgePoint());
        map.put("difficulty", question.getDifficulty());
        map.put("track", question.getTrack());
        map.put("lessonId", question.getLessonId());
        map.put("status", question.getStatus());
        if (includeAnswer) {
            map.put("correctAnswer", question.getCorrectAnswer());
            map.put("answerKeywords", question.getAnswerKeywords());
        }
        return map;
    }

    private Map<String, Object> toRecordMap(PracticeAnswerRecord record) {
        Map<String, Object> map = new HashMap<>();
        PracticeQuestion question = questionMapper.selectById(record.getQuestionId());
        map.put("id", record.getId());
        map.put("questionId", record.getQuestionId());
        map.put("questionTitle", question == null ? "题目已删除" : question.getTitle());
        map.put("answeredAt", record.getAnsweredAt());
        map.put("durationSeconds", record.getDurationSeconds());
        map.put("userAnswer", record.getUserAnswer());
        map.put("correctAnswer", record.getCorrectAnswer());
        map.put("correct", record.getCorrect());
        map.put("judgeScore", record.getJudgeScore());
        map.put("judgeFeedback", record.getJudgeFeedback());
        map.put("points", record.getPoints());
        map.put("chapter", record.getChapter());
        map.put("knowledgePoint", record.getKnowledgePoint());
        map.put("difficulty", record.getDifficulty());
        map.put("track", record.getTrack());
        map.put("questionType", record.getQuestionType());
        map.put("attemptNumber", record.getAttemptNumber());
        map.put("sourceMode", record.getSourceMode());
        map.put("doubtful", record.getDoubtful());
        map.put("reviewNote", record.getReviewNote());
        map.put("analysis", question == null ? "" : question.getAnalysis());
        return map;
    }

    private Map<String, Object> toMistakeMap(PracticeMistake mistake) {
        PracticeQuestion question = questionMapper.selectById(mistake.getQuestionId());
        Map<String, Object> map = new HashMap<>();
        map.put("id", mistake.getId());
        map.put("questionId", mistake.getQuestionId());
        map.put("questionTitle", question == null ? "题目已删除" : question.getTitle());
        map.put("content", question == null ? "" : question.getContent());
        map.put("correctAnswer", question == null ? "" : question.getCorrectAnswer());
        map.put("analysis", question == null ? "" : question.getAnalysis());
        map.put("chapter", question == null ? "" : question.getChapter());
        map.put("knowledgePoint", question == null ? "" : question.getKnowledgePoint());
        map.put("difficulty", question == null ? "" : question.getDifficulty());
        map.put("questionType", question == null ? "" : question.getQuestionType());
        map.put("subject", question == null ? "" : question.getSubject());
        map.put("track", question == null ? "" : question.getTrack());
        map.put("status", mistake.getStatus());
        map.put("wrongCount", mistake.getWrongCount());
        map.put("reviewCount", mistake.getReviewCount());
        map.put("doubtful", mistake.getDoubtful());
        map.put("reviewNote", mistake.getReviewNote());
        map.put("lastWrongAt", mistake.getLastWrongAt());
        map.put("lastReviewAt", mistake.getLastReviewAt());
        map.put("resolvedAt", mistake.getResolvedAt());
        return map;
    }

    private List<Map<String, Object>> groupStatistics(List<PracticeAnswerRecord> records,
                                                      Function<PracticeAnswerRecord, String> classifier) {
        return records.stream()
                .collect(Collectors.groupingBy(classifier, LinkedHashMap::new, Collectors.toList()))
                .entrySet().stream()
                .map(entry -> statGroup(entry.getKey(), entry.getValue()))
                .sorted((a, b) -> Long.compare(((Number) b.get("count")).longValue(), ((Number) a.get("count")).longValue()))
                .collect(Collectors.toList());
    }

    private List<PracticeAnswerRecord> previousPeriodRecords(Long userId, String range) {
        LocalDate today = LocalDate.now();
        LocalDateTime currentStart;
        LocalDateTime previousStart;
        switch (range) {
            case "today" -> {
                currentStart = today.atStartOfDay();
                previousStart = today.minusDays(1).atStartOfDay();
            }
            case "month" -> {
                currentStart = today.minusDays(29).atStartOfDay();
                previousStart = today.minusDays(59).atStartOfDay();
            }
            case "week" -> {
                currentStart = today.minusDays(6).atStartOfDay();
                previousStart = today.minusDays(13).atStartOfDay();
            }
            default -> {
                return List.of();
            }
        }
        return recordMapper.selectList(new QueryWrapper<PracticeAnswerRecord>()
                .eq("user_id", userId)
                .ge("answered_at", previousStart)
                .lt("answered_at", currentStart)
                .orderByAsc("answered_at"));
    }

    private double accuracyPercent(List<PracticeAnswerRecord> records) {
        if (records == null || records.isEmpty()) {
            return 0;
        }
        long correct = records.stream().filter(record -> Boolean.TRUE.equals(record.getCorrect())).count();
        return Math.round(correct * 1000.0 / records.size()) / 10.0;
    }

    private Map<String, Object> knowledgeStat(String name, List<PracticeAnswerRecord> records) {
        Map<String, Object> map = new LinkedHashMap<>(statGroup(name, records));
        List<PracticeAnswerRecord> ordered = records.stream()
                .sorted(Comparator.comparing(record -> record.getAnsweredAt() == null ? LocalDateTime.MIN : record.getAnsweredAt()))
                .collect(Collectors.toList());
        int count = ordered.size();
        long correct = ordered.stream().filter(record -> Boolean.TRUE.equals(record.getCorrect())).count();
        int recentStart = Math.max(0, count - 5);
        int previousStart = Math.max(0, recentStart - 5);
        List<PracticeAnswerRecord> recent = ordered.subList(recentStart, count);
        List<PracticeAnswerRecord> previous = ordered.subList(previousStart, recentStart);
        double recentAccuracy = weightedAccuracy(recent);
        double previousAccuracy = previous.isEmpty() ? recentAccuracy : weightedAccuracy(previous);
        double smoothedAccuracy = (correct + 2.5) / (count + 5.0);
        double sampleConfidence = Math.min(1.0, count / 8.0);
        double masteryScore = roundOne((smoothedAccuracy * 0.55 + recentAccuracy / 100.0 * 0.30 + sampleConfidence * 0.15) * 100.0);
        if (count < 3) {
            masteryScore = Math.min(69.0, masteryScore);
        }
        double recentTrend = previous.isEmpty() ? 0 : roundOne(recentAccuracy - previousAccuracy);
        String level = masteryLevel(masteryScore, count);

        map.put("recentAccuracy", recentAccuracy);
        map.put("recentTrend", recentTrend);
        map.put("sampleConfidence", roundOne(sampleConfidence * 100));
        map.put("masteryScore", masteryScore);
        map.put("masteryLevel", level);
        map.put("needsReview", count < 3 || masteryScore < 70 || recentAccuracy < 60);
        map.put("recommendation", masteryRecommendation(name, level, count, recentTrend));
        map.put("lastAnsweredAt", ordered.isEmpty() ? null : ordered.get(ordered.size() - 1).getAnsweredAt());
        return map;
    }

    private double weightedAccuracy(List<PracticeAnswerRecord> records) {
        if (records.isEmpty()) return 0;
        double weightSum = 0;
        double correctWeight = 0;
        for (int index = 0; index < records.size(); index++) {
            double weight = index + 1;
            weightSum += weight;
            if (Boolean.TRUE.equals(records.get(index).getCorrect())) {
                correctWeight += weight;
            }
        }
        return roundOne(correctWeight * 100.0 / weightSum);
    }

    private String masteryLevel(double masteryScore, int count) {
        if (count < 3) return "数据不足";
        if (masteryScore >= 85) return "已掌握";
        if (masteryScore >= 70) return "较熟练";
        if (masteryScore >= 55) return "待巩固";
        return "薄弱";
    }

    private String masteryRecommendation(String name, String level, int count, double trend) {
        if (count < 3) return "再完成 " + (3 - count) + " 题后可形成较可靠判断";
        if ("薄弱".equals(level)) return "优先复习“" + name + "”基础概念并重做错题";
        if ("待巩固".equals(level)) return trend < 0 ? "近期表现下降，建议进行一次专项练习" : "继续完成 3—5 道同类题巩固";
        if ("较熟练".equals(level)) return "增加一道进阶题验证迁移能力";
        return "掌握稳定，可降低复习频率";
    }

    private List<Map<String, Object>> wrongReasonDistribution(List<PracticeAnswerRecord> records, double averageSeconds) {
        Map<String, Long> counts = records.stream()
                .filter(record -> !Boolean.TRUE.equals(record.getCorrect()))
                .collect(Collectors.groupingBy(record -> wrongReasonCode(record, averageSeconds), LinkedHashMap::new, Collectors.counting()));
        return counts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .map(entry -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("reason", entry.getKey());
                    item.put("label", wrongReasonLabel(entry.getKey()));
                    item.put("description", wrongReasonDescription(entry.getKey()));
                    item.put("count", entry.getValue());
                    item.put("inferred", true);
                    return item;
                })
                .collect(Collectors.toList());
    }

    private String wrongReasonCode(PracticeAnswerRecord record, double averageSeconds) {
        String feedback = record.getJudgeFeedback() == null ? "" : record.getJudgeFeedback().toLowerCase(Locale.ROOT);
        if (Boolean.TRUE.equals(record.getDoubtful()) || feedback.contains("概念") || feedback.contains("理解")) {
            return "concept_unclear";
        }
        int duration = record.getDurationSeconds() == null ? 0 : record.getDurationSeconds();
        if (duration > 0 && duration <= Math.max(8, averageSeconds * 0.35)) {
            return "too_fast";
        }
        if (record.getAttemptNumber() != null && record.getAttemptNumber() > 1) {
            return "repeated_error";
        }
        if (duration >= Math.max(90, averageSeconds * 1.6)) {
            return "method_unfamiliar";
        }
        if ("SHORT_ANSWER".equals(record.getQuestionType()) || "FILL_BLANK".equals(record.getQuestionType())) {
            return "expression_incomplete";
        }
        return "application_error";
    }

    private String wrongReasonLabel(String code) {
        return switch (code) {
            case "concept_unclear" -> "概念理解不清";
            case "too_fast" -> "审题过快";
            case "repeated_error" -> "重复出错";
            case "method_unfamiliar" -> "解题方法不熟";
            case "expression_incomplete" -> "答案表述不完整";
            default -> "知识应用错误";
        };
    }

    private String wrongReasonDescription(String code) {
        return switch (code) {
            case "concept_unclear" -> "疑问题或反馈中出现概念理解线索";
            case "too_fast" -> "错误题用时明显低于个人平均水平";
            case "repeated_error" -> "同一题或同类题发生再次错误";
            case "method_unfamiliar" -> "错误题用时明显偏长";
            case "expression_incomplete" -> "填空或简答题答案未完整命中";
            default -> "选择或计算结果与标准答案不一致";
        };
    }

    private List<String> buildRecommendations(List<PracticeAnswerRecord> records,
                                              List<Map<String, Object>> knowledgeDistribution,
                                              List<Map<String, Object>> questionTypeDistribution,
                                              double accuracy,
                                              double accuracyChange,
                                              double averageSeconds) {
        List<String> recommendations = new ArrayList<>();
        if (records.isEmpty()) {
            recommendations.add("当前周期暂无答题数据，完成至少 3 道题后生成个性化分析。");
            return recommendations;
        }
        List<String> weakNames = knowledgeDistribution.stream()
                .filter(item -> Boolean.TRUE.equals(item.get("needsReview")))
                .map(item -> String.valueOf(item.get("name")))
                .limit(3)
                .collect(Collectors.toList());
        if (!weakNames.isEmpty()) {
            recommendations.add("优先复习：" + String.join("、", weakNames) + "，建议从错题重做开始。");
        }
        if (accuracy < 60) {
            recommendations.add("当前正确率低于 60%，先降低单次练习量并在每题后复盘错误原因。");
        } else if (accuracy >= 85) {
            recommendations.add("整体正确率较高，可增加进阶题和跨知识点综合题。");
        }
        if (accuracyChange <= -5) {
            recommendations.add("正确率较上一周期下降 " + Math.abs(accuracyChange) + " 个百分点，建议检查近期新增知识点。");
        }
        questionTypeDistribution.stream()
                .filter(item -> ((Number) item.get("count")).longValue() >= 3)
                .min(Comparator.comparingDouble(item -> ((Number) item.get("accuracy")).doubleValue()))
                .filter(item -> ((Number) item.get("accuracy")).doubleValue() < 70)
                .ifPresent(item -> recommendations.add("题型薄弱项为“" + statisticLabel(String.valueOf(item.get("name"))) + "”，可安排专项训练。"));
        if (averageSeconds > 90) {
            recommendations.add("平均每题用时超过 90 秒，建议总结常用公式和解题步骤以提升速度。");
        }
        if (recommendations.isEmpty()) {
            recommendations.add("本周期表现稳定，保持练习频率并定期回顾错题即可。");
        }
        return recommendations;
    }

    private String statisticLabel(String value) {
        return switch (value) {
            case "SINGLE_CHOICE" -> "单选题";
            case "MULTIPLE_CHOICE" -> "多选题";
            case "FILL_BLANK" -> "填空题";
            case "SHORT_ANSWER" -> "简答题";
            case "BASIC" -> "基础";
            case "ADVANCED" -> "进阶";
            case "HARD" -> "困难";
            default -> value;
        };
    }

    private int bestStreak(List<PracticeAnswerRecord> records) {
        List<LocalDate> dates = records.stream()
                .map(PracticeAnswerRecord::getAnsweredAt)
                .filter(Objects::nonNull)
                .map(LocalDateTime::toLocalDate)
                .distinct()
                .sorted()
                .collect(Collectors.toList());
        int best = 0;
        int current = 0;
        LocalDate previous = null;
        for (LocalDate date : dates) {
            current = previous != null && previous.plusDays(1).equals(date) ? current + 1 : 1;
            best = Math.max(best, current);
            previous = date;
        }
        return best;
    }

    private String normalizedGroupName(String value, String fallback) {
        return StringUtils.hasText(value) ? value : fallback;
    }

    private double roundOne(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    private Map<String, Object> statGroup(String name, List<PracticeAnswerRecord> records) {
        long correct = records.stream().filter(record -> Boolean.TRUE.equals(record.getCorrect())).count();
        int duration = records.stream().map(PracticeAnswerRecord::getDurationSeconds).filter(Objects::nonNull).mapToInt(Integer::intValue).sum();
        Map<String, Object> map = new HashMap<>();
        map.put("name", name);
        map.put("count", records.size());
        map.put("correct", correct);
        map.put("accuracy", records.isEmpty() ? 0 : Math.round(correct * 1000.0 / records.size()) / 10.0);
        map.put("durationSeconds", duration);
        map.put("averageDurationSeconds", records.isEmpty() ? 0 : Math.round(duration * 10.0 / records.size()) / 10.0);
        return map;
    }

    private String rankTitle(int points) {
        if (points >= 300) {
            return "备考达人";
        }
        if (points >= 120) {
            return "进阶刷题人";
        }
        return "入门学习者";
    }

    private String normalizeMode(String mode) {
        if (!StringUtils.hasText(mode)) {
            return MODE_SEQUENTIAL;
        }
        String upper = mode.toUpperCase(Locale.ROOT);
        if ("RANDOM_COMPREHENSIVE".equals(upper)) return MODE_RANDOM;
        if ("WRONG_REDO".equals(upper)) return MODE_MISTAKE_REDO;
        return upper;
    }

    private List<String> answerCandidates(PracticeQuestion question) {
        List<String> answers = new ArrayList<>();
        answers.add(question.getCorrectAnswer());
        if (StringUtils.hasText(question.getAnswerKeywords())) {
            answers.addAll(Arrays.stream(question.getAnswerKeywords().split("[;；,，|]"))
                    .map(String::trim)
                    .filter(StringUtils::hasText)
                    .collect(Collectors.toList()));
        }
        return answers.stream().filter(StringUtils::hasText).distinct().collect(Collectors.toList());
    }

    private String normalizeChoice(String value) {
        return nvl(value, "").replaceAll("[^A-Za-z]", "").toUpperCase(Locale.ROOT);
    }

    private String normalizeChoiceSet(String value) {
        return normalizeChoice(value).chars().sorted()
                .mapToObj(c -> String.valueOf((char) c))
                .collect(Collectors.joining());
    }

    private String normalizeText(String value) {
        String normalized = Normalizer.normalize(nvl(value, ""), Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", "")
                .replaceAll("[，。！？、；;,.!?]", "");
        return normalized.trim();
    }

    private boolean numericEquals(String answer, String userAnswer) {
        try {
            BigDecimal a = new BigDecimal(answer.trim());
            BigDecimal b = new BigDecimal(userAnswer.trim());
            return a.subtract(b).abs().compareTo(new BigDecimal("0.0001")) <= 0;
        } catch (Exception e) {
            return false;
        }
    }

    private double similarity(String a, String b) {
        if (!StringUtils.hasText(a) || !StringUtils.hasText(b)) {
            return 0;
        }
        Set<String> aa = new HashSet<>(Arrays.asList(a.split("")));
        Set<String> bb = new HashSet<>(Arrays.asList(b.split("")));
        Set<String> union = new HashSet<>(aa);
        union.addAll(bb);
        Set<String> intersection = new HashSet<>(aa);
        intersection.retainAll(bb);
        return union.isEmpty() ? 0 : intersection.size() * 1.0 / union.size();
    }

    private String required(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    private String str(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private int parseInt(Object value, int fallback) {
        try {
            return value == null ? fallback : Integer.parseInt(String.valueOf(value));
        } catch (Exception e) {
            return fallback;
        }
    }

    private int nvl(Integer value) {
        return value == null ? 0 : value;
    }

    private String nvl(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }

    private record JudgeResult(boolean correct, Double score, String feedback) {
    }

}
