package com.treepeople.leapmindtts.controller;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.treepeople.leapmindtts.pojo.dto.ExerciseRecordRequest;
import com.treepeople.leapmindtts.pojo.result.ApiResponse;
import com.treepeople.leapmindtts.pojo.dto.MarkReviewedRequest;
import com.treepeople.leapmindtts.pojo.dto.profile.M6Dtos.EventAck;
import com.treepeople.leapmindtts.pojo.dto.profile.M6Dtos.LearningEventRequest;
import com.treepeople.leapmindtts.pojo.vo.ExerciseVO;
import com.treepeople.leapmindtts.pojo.vo.ReviewReminderVO;
import com.treepeople.leapmindtts.service.PracticeService;
import com.treepeople.leapmindtts.service.lesson.WeakPointsService;
import com.treepeople.leapmindtts.service.profile.UserEventService;
import com.treepeople.leapmindtts.service.user.ReviewReminderService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.io.ByteArrayOutputStream;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/practice")
@RequiredArgsConstructor
public class PracticeController {

    private final PracticeService practiceService;
    private final ReviewReminderService reviewReminderService;
    private final WeakPointsService weakPointsService;
    private final UserEventService userEventService;

    @GetMapping("/filters")
    public ResponseEntity<ApiResponse<Map<String, Object>>> filters() {
        return ResponseEntity.ok(ApiResponse.success(practiceService.getFilters(), "获取筛选项成功"));
    }

    @GetMapping("/questions")
    public ResponseEntity<ApiResponse<Map<String, Object>>> listQuestions(@RequestParam Map<String, Object> params) {
        return ResponseEntity.ok(ApiResponse.success(practiceService.listQuestions(params), "获取题库列表成功"));
    }

    @GetMapping("/questions/{questionId}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> questionDetail(@PathVariable Long questionId) {
        return ResponseEntity.ok(ApiResponse.success(practiceService.getQuestionDetail(questionId), "获取题目详情成功"));
    }

    @PostMapping("/questions")
    public ResponseEntity<ApiResponse<Map<String, Object>>> createQuestion(@RequestBody QuestionRequest body) {
        return ResponseEntity.ok(ApiResponse.success(practiceService.createQuestion(body), "题目创建成功"));
    }

    @PutMapping("/questions/{questionId}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> updateQuestion(@PathVariable Long questionId, @RequestBody QuestionRequest body) {
        return ResponseEntity.ok(ApiResponse.success(practiceService.updateQuestion(questionId, body), "题目更新成功"));
    }

    @DeleteMapping("/questions/{questionId}")
    public ResponseEntity<ApiResponse<Void>> deleteQuestion(@PathVariable Long questionId) {
        practiceService.deleteQuestion(questionId);
        return ResponseEntity.ok(ApiResponse.success(null, "题目删除成功"));
    }

    @PostMapping(value = "/questions/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<Map<String, Object>>> importQuestions(@RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(ApiResponse.success(practiceService.importQuestions(file), "题目导入完成"));
    }

    @GetMapping("/questions/import-template")
    public ResponseEntity<byte[]> importTemplate() {
        String[] headers = {"subject", "gradeLevel", "track", "chapter", "knowledgePoint", "questionType", "difficulty", "title", "content",
                "optionA", "optionB", "optionC", "optionD", "correctAnswer", "answerKeywords", "analysis", "lessonId", "status"};
        String[] example = {"数学", "大学", "高数期末", "函数极限", "重要极限", "SINGLE_CHOICE", "BASIC", "极限计算",
                "lim x->0 sin(x)/x 的值是？", "0", "1", "不存在", "无穷大", "B", "", "这是重要基本极限，值为 1。", "", "ENABLED"};
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("题目导入");
            Row headerRow = sheet.createRow(0);
            Row exampleRow = sheet.createRow(1);
            for (int index = 0; index < headers.length; index++) {
                headerRow.createCell(index).setCellValue(headers[index]);
                exampleRow.createCell(index).setCellValue(example[index]);
                sheet.setColumnWidth(index, Math.min(50, Math.max(12, Math.max(headers[index].length(), example[index].length()) + 2)) * 256);
            }
            workbook.write(output);
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                            .filename("practice-question-template.xlsx", StandardCharsets.UTF_8)
                            .build()
                            .toString())
                    .body(output.toByteArray());
        } catch (Exception exception) {
            throw new IllegalStateException("导入模板生成失败", exception);
        }
    }

    @GetMapping("/next")
    public ResponseEntity<ApiResponse<Map<String, Object>>> nextQuestion(
            HttpServletRequest request,
            @RequestParam(defaultValue = "SEQUENTIAL") String mode,
            @RequestParam(required = false) String subject,
            @RequestParam(required = false) String gradeLevel,
            @RequestParam(required = false) String track,
            @RequestParam(required = false) String chapter,
            @RequestParam(required = false) String knowledgePoint,
            @RequestParam(required = false) String questionType,
            @RequestParam(required = false) String difficulty,
            @RequestParam(required = false) String lessonId) {
        return ResponseEntity.ok(ApiResponse.success(
                practiceService.getNextQuestion(currentUserId(request), mode, subject, gradeLevel, track, chapter, knowledgePoint, questionType, difficulty, lessonId),
                "获取题目成功"));
    }

    @PostMapping("/submit")
    public ResponseEntity<ApiResponse<Map<String, Object>>> submit(
            HttpServletRequest request,
            @RequestBody SubmitAnswerRequest body) {
        Long userId = currentUserId(request);
        Map<String, Object> result = practiceService.submitAnswer(userId, body);
        recordWeakPointBestEffort(userId, result);
        recordAnswerEventBestEffort(userId, body, result, request);
        return ResponseEntity.ok(ApiResponse.success(result, "提交答案成功"));
    }

    @GetMapping("/recommendations")
    public ResponseEntity<ApiResponse<List<ExerciseVO>>> recommendations(
            HttpServletRequest request,
            @RequestParam(required = false) String subject,
            @RequestParam(required = false) String knowledgePoint,
            @RequestParam(defaultValue = "5") Integer count) {
        int safeCount = Math.max(1, Math.min(20, count == null ? 5 : count));
        return ResponseEntity.ok(ApiResponse.success(
                weakPointsService.recommendExercises(currentUserId(request), subject, knowledgePoint, safeCount),
                "获取薄弱点练习推荐成功"));
    }

    @PostMapping("/sessions/complete")
    public ResponseEntity<ApiResponse<EventAck>> completeSession(
            HttpServletRequest request,
            @RequestBody CompleteSessionRequest body) {
        Long userId = currentUserId(request);
        if (body.getQuestionCount() == null || body.getQuestionCount() < 1) {
            throw new IllegalArgumentException("questionCount 必须大于 0");
        }
        int correctCount = body.getCorrectCount() == null ? 0 : body.getCorrectCount();
        if (correctCount < 0 || correctCount > body.getQuestionCount()) {
            throw new IllegalArgumentException("correctCount 超出有效范围");
        }
        int durationSeconds = body.getDurationSeconds() == null ? 0 : Math.max(0, body.getDurationSeconds());
        String sessionId = body.getSessionId();
        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException("sessionId 不能为空");
        }

        ObjectNode data = JsonNodeFactory.instance.objectNode();
        data.put("questionCount", body.getQuestionCount());
        data.put("accuracy", correctCount / (double) body.getQuestionCount());
        data.put("durationSec", Math.min(86400, durationSeconds));
        String eventId = "m1-finish:" + UUID.nameUUIDFromBytes(
                (userId + ":" + sessionId).getBytes(StandardCharsets.UTF_8));
        LearningEventRequest event = new LearningEventRequest(
                eventId,
                userId,
                "finish_practice",
                "M1",
                OffsetDateTime.now(ZoneOffset.UTC),
                "1.0",
                sessionId,
                null,
                null,
                data);
        EventAck ack = userEventService.record(userId, event, request);
        return ResponseEntity.ok(ApiResponse.success(ack, "练习完成事件已记录"));
    }

    @GetMapping("/records")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> records(
            HttpServletRequest request,
            @RequestParam(defaultValue = "all") String range,
            @RequestParam(required = false) String chapter,
            @RequestParam(required = false) String knowledgePoint,
            @RequestParam(required = false) Boolean wrongOnly) {
        return ResponseEntity.ok(ApiResponse.success(
                practiceService.getRecords(currentUserId(request), range, chapter, knowledgePoint, wrongOnly),
                "获取答题记录成功"));
    }

    @GetMapping("/mistakes")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> mistakes(
            HttpServletRequest request,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String chapter,
            @RequestParam(required = false) String knowledgePoint) {
        return ResponseEntity.ok(ApiResponse.success(
                practiceService.getMistakes(currentUserId(request), status, chapter, knowledgePoint),
                "获取错题本成功"));
    }

    @PatchMapping("/mistake-book/{mistakeId}")
    public ResponseEntity<ApiResponse<Void>> updateMistakeBook(
            HttpServletRequest request,
            @PathVariable Long mistakeId,
            @RequestBody UpdateMistakeRequest body) {
        practiceService.updateMistakeStatus(currentUserId(request), mistakeId, body);
        return ResponseEntity.ok(ApiResponse.success(null, "错题状态已更新"));
    }

    @GetMapping("/dashboard")
    public ResponseEntity<ApiResponse<Map<String, Object>>> dashboard(HttpServletRequest request) {
        return ResponseEntity.ok(ApiResponse.success(practiceService.getDashboard(currentUserId(request)), "获取成长看板成功"));
    }

    @GetMapping("/statistics")
    public ResponseEntity<ApiResponse<Map<String, Object>>> statistics(
            HttpServletRequest request,
            @RequestParam(defaultValue = "week") String range) {
        return ResponseEntity.ok(ApiResponse.success(practiceService.getStatistics(currentUserId(request), range), "获取练习统计成功"));
    }

    @GetMapping("/review-reminders")
    public ResponseEntity<ApiResponse<List<ReviewReminderVO>>> reviewReminders(HttpServletRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                reviewReminderService.getReviewReminders(currentUserId(request)),
                "获取待复习提醒成功"));
    }

    @PostMapping("/review-reminders/{reminderId}/complete")
    public ResponseEntity<ApiResponse<ReviewReminderVO>> completeReviewReminder(
            HttpServletRequest request,
            @PathVariable Long reminderId,
            @RequestBody(required = false) CompleteReviewRequest body) {
        MarkReviewedRequest markReviewedRequest = new MarkReviewedRequest();
        markReviewedRequest.setReminderId(reminderId);
        markReviewedRequest.setNotes(body == null ? null : body.getNotes());
        return ResponseEntity.ok(ApiResponse.success(
                reviewReminderService.markAsReviewed(currentUserId(request), markReviewedRequest),
                "复习任务已完成"));
    }

    @GetMapping("/leaderboards")
    public ResponseEntity<ApiResponse<Map<String, Object>>> leaderboards(
            HttpServletRequest request,
            @RequestParam(required = false) String track) {
        return ResponseEntity.ok(ApiResponse.success(practiceService.getLeaderboards(currentUserId(request), track), "获取榜单成功"));
    }

    @PostMapping("/checkin")
    public ResponseEntity<ApiResponse<Map<String, Object>>> checkin(HttpServletRequest request) {
        return ResponseEntity.ok(ApiResponse.success(practiceService.checkin(currentUserId(request)), "签到成功"));
    }

    @GetMapping("/checkin/status")
    public ResponseEntity<ApiResponse<Map<String, Object>>> checkinStatus(HttpServletRequest request) {
        return ResponseEntity.ok(ApiResponse.success(practiceService.getCheckinStatus(currentUserId(request)), "获取签到状态成功"));
    }

    @PatchMapping("/privacy")
    public ResponseEntity<ApiResponse<Void>> privacy(HttpServletRequest request, @RequestBody PrivacyRequest body) {
        practiceService.updatePrivacy(currentUserId(request), Boolean.TRUE.equals(body.getHidden()));
        return ResponseEntity.ok(ApiResponse.success(null, "隐私设置已更新"));
    }

    @PatchMapping("/mistakes/{recordId}")
    public ResponseEntity<ApiResponse<Void>> updateMistake(
            HttpServletRequest request,
            @PathVariable Long recordId,
            @RequestBody UpdateMistakeRequest body) {
        practiceService.updateMistake(currentUserId(request), recordId, body);
        return ResponseEntity.ok(ApiResponse.success(null, "错题笔记已更新"));
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> export(HttpServletRequest request, @RequestParam(defaultValue = "false") boolean wrongOnly) {
        String content = practiceService.exportRecords(currentUserId(request), wrongOnly);
        String filename = wrongOnly ? "mistakes.txt" : "practice-records.txt";
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "plain", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(filename, StandardCharsets.UTF_8)
                        .build()
                        .toString())
                .body(content.getBytes(StandardCharsets.UTF_8));
    }

    @DeleteMapping("/records")
    public ResponseEntity<ApiResponse<Void>> clear(HttpServletRequest request) {
        practiceService.clearUserData(currentUserId(request));
        return ResponseEntity.ok(ApiResponse.success(null, "个人刷题数据已清空"));
    }

    private Long currentUserId(HttpServletRequest request) {
        Object userId = request.getAttribute("userId");
        if (userId instanceof Long id) {
            return id;
        }
        throw new IllegalStateException("未获取到登录用户");
    }

    @SuppressWarnings("unchecked")
    private void recordWeakPointBestEffort(Long userId, Map<String, Object> result) {
        try {
            Map<String, Object> record = (Map<String, Object>) result.get("record");
            Map<String, Object> question = (Map<String, Object>) result.get("question");
            if (record == null || question == null || record.get("id") == null || question.get("id") == null) {
                return;
            }
            ExerciseRecordRequest weakPointRecord = ExerciseRecordRequest.builder()
                    .userId(userId)
                    .exerciseId(String.valueOf(question.get("id")))
                    .knowledgePoint(stringValue(question.get("knowledgePoint")))
                    .subject(stringValue(question.get("subject")))
                    .isCorrect(Boolean.TRUE.equals(result.get("correct")) ? 1 : 0)
                    .build();
            weakPointsService.recordExerciseResult(weakPointRecord);
        } catch (RuntimeException exception) {
            log.warn("M1 答题结果回写薄弱点模块失败，不影响主答题记录: userId={}, reason={}",
                    userId, exception.getClass().getSimpleName());
        }
    }

    @SuppressWarnings("unchecked")
    private void recordAnswerEventBestEffort(
            Long userId,
            SubmitAnswerRequest body,
            Map<String, Object> result,
            HttpServletRequest request) {
        try {
            Map<String, Object> record = (Map<String, Object>) result.get("record");
            Map<String, Object> question = (Map<String, Object>) result.get("question");
            if (record == null || question == null || record.get("id") == null) {
                return;
            }
            ObjectNode data = JsonNodeFactory.instance.objectNode();
            data.put("isCorrect", Boolean.TRUE.equals(result.get("correct")));
            data.put("difficulty", difficultyLevel(question.get("difficulty")));
            data.put("timeSpentSec", Math.min(86400, Math.max(0,
                    body.getDurationSeconds() == null ? 0 : body.getDurationSeconds())));
            data.put("hintCount", 0);
            LearningEventRequest event = new LearningEventRequest(
                    "m1-answer:" + record.get("id"),
                    userId,
                    "answer_question",
                    "M1",
                    OffsetDateTime.now(ZoneOffset.UTC),
                    "1.0",
                    body.getSessionId(),
                    null,
                    null,
                    data);
            userEventService.record(userId, event, request);
        } catch (RuntimeException exception) {
            log.warn("M1 答题事件写入 M6 失败，不影响主答题记录: userId={}, reason={}",
                    userId, exception.getClass().getSimpleName());
        }
    }

    private int difficultyLevel(Object difficulty) {
        return switch (stringValue(difficulty).toUpperCase()) {
            case "HARD" -> 5;
            case "ADVANCED", "MEDIUM" -> 3;
            default -> 1;
        };
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    @Data
    public static class SubmitAnswerRequest {
        private Long questionId;
        private String userAnswer;
        private Integer durationSeconds;
        private String mode;
        private String sessionId;
    }

    @Data
    public static class CompleteSessionRequest {
        private String sessionId;
        private Integer questionCount;
        private Integer correctCount;
        private Integer durationSeconds;
    }

    @Data
    public static class QuestionRequest {
        private String subject;
        private String gradeLevel;
        private String questionType;
        private String title;
        private String content;
        private String optionA;
        private String optionB;
        private String optionC;
        private String optionD;
        private String correctAnswer;
        private String answerKeywords;
        private String analysis;
        private String chapter;
        private String knowledgePoint;
        private String difficulty;
        private String track;
        private String lessonId;
        private String status;
    }

    @Data
    public static class UpdateMistakeRequest {
        private Boolean doubtful;
        private String reviewNote;
        private String status;
    }

    @Data
    public static class PrivacyRequest {
        private Boolean hidden;
    }

    @Data
    public static class CompleteReviewRequest {
        private String notes;
    }
}
