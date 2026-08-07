package com.treepeople.leapmindtts.service.profile.platform;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.math.BigDecimal;
import java.util.Set;
import java.util.regex.Pattern;

/** Payload records mirror LearningEventPolicy's public v1 data schema exactly. */
public sealed interface LearningEventPayload permits LearningEventPayload.AnswerQuestion,
        LearningEventPayload.FinishPractice, LearningEventPayload.RequestExplanation,
        LearningEventPayload.ExplanationFeedback, LearningEventPayload.WeakPointChanged,
        LearningEventPayload.LectureInteract, LearningEventPayload.LessonMaterialUsed,
        LearningEventPayload.AskDoubt, LearningEventPayload.MarkReviewed,
        LearningEventPayload.PreferenceChanged {
    Pattern IDENTIFIER = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._:-]{0,63}");
    Set<String> CONFUSION = Set.of("concept_unclear", "formula_confusion", "step_unclear", "application_difficulty", "careless_error");
    @JsonIgnore String eventType();
    @JsonIgnore String sourceModule();
    @JsonIgnore default String schemaVersion() { return "1.0"; }

    record AnswerQuestion(boolean isCorrect, int difficulty, int timeSpentSec, int hintCount, String confusionTag) implements LearningEventPayload {
        public AnswerQuestion { between(difficulty, 1, 5, "difficulty"); between(timeSpentSec, 0, 86400, "timeSpentSec"); between(hintCount, 0, 100, "hintCount"); optional(confusionTag, CONFUSION, "confusionTag"); }
        public String eventType() { return "answer_question"; } public String sourceModule() { return "M1"; }
    }
    record FinishPractice(int questionCount, BigDecimal accuracy, int durationSec) implements LearningEventPayload {
        public FinishPractice { between(questionCount, 1, 10000, "questionCount"); fraction(accuracy, "accuracy"); between(durationSec, 0, 86400, "durationSec"); }
        public String eventType() { return "finish_practice"; } public String sourceModule() { return "M1"; }
    }
    record RequestExplanation(String explainId, String reasonTag) implements LearningEventPayload {
        public RequestExplanation { identifier(explainId, "explainId"); required(reasonTag, Set.of("WRONG_ANSWER", "REPEATED_ERROR", "USER_REQUEST", "LOW_CONFIDENCE", "REVIEW_NEEDED"), "reasonTag"); }
        public String eventType() { return "request_explanation"; } public String sourceModule() { return "M2"; }
    }
    record ExplanationFeedback(String explainId, String feedback, int repeatCount) implements LearningEventPayload {
        public ExplanationFeedback { identifier(explainId, "explainId"); required(feedback, Set.of("understood", "partly_understood", "still_confused"), "feedback"); between(repeatCount, 0, 100, "repeatCount"); }
        public String eventType() { return "explanation_feedback"; } public String sourceModule() { return "M2"; }
    }
    record WeakPointChanged(BigDecimal oldScore, BigDecimal newScore, String reason) implements LearningEventPayload {
        public WeakPointChanged { fraction(oldScore, "oldScore"); fraction(newScore, "newScore"); required(reason, Set.of("ACCURACY_DROP", "REPEATED_ERROR", "TEACHER_MARKED", "RECALCULATED"), "reason"); }
        public String eventType() { return "weak_point_changed"; } public String sourceModule() { return "M3"; }
    }
    record LectureInteract(String lectureId, String chapterId, String action) implements LearningEventPayload {
        public LectureInteract { identifier(lectureId, "lectureId"); identifier(chapterId, "chapterId"); required(action, Set.of("pause", "resume", "replay", "ask", "complete"), "action"); }
        public String eventType() { return "lecture_interact"; } public String sourceModule() { return "M4"; }
    }
    record LessonMaterialUsed(String contentId, String materialType, String result) implements LearningEventPayload {
        public LessonMaterialUsed { identifier(contentId, "contentId"); required(materialType, Set.of("text", "image", "audio", "video", "exercise"), "materialType"); required(result, Set.of("completed", "skipped", "helpful", "not_helpful"), "result"); }
        public String eventType() { return "lesson_material_used"; } public String sourceModule() { return "M5"; }
    }
    record AskDoubt(String topic, String confusionTag, boolean isFollowUp) implements LearningEventPayload {
        public AskDoubt { if (topic == null || topic.isBlank() || topic.codePointCount(0, topic.length()) > 120) throw new IllegalArgumentException("topic is invalid"); required(confusionTag, CONFUSION, "confusionTag"); }
        public String eventType() { return "ask_doubt"; } public String sourceModule() { return "M7"; }
    }
    record MarkReviewed(String result, int timeSpentSec, int hintCount) implements LearningEventPayload {
        public MarkReviewed { required(result, Set.of("correct_without_hint", "correct_with_hint", "incorrect", "still_confused", "postponed"), "result"); between(timeSpentSec, 0, 86400, "timeSpentSec"); between(hintCount, 0, 100, "hintCount"); }
        public String eventType() { return "mark_reviewed"; } public String sourceModule() { return "M6"; }
    }
    record PreferenceChanged(String preferenceKey, String preferenceValue) implements LearningEventPayload {
        public PreferenceChanged {
            Set<String> values = switch (preferenceKey) {
                case "content_mode" -> Set.of("text", "image", "audio", "video", "exercise");
                case "explanation_style" -> Set.of("step_by_step", "example_first", "concise", "detailed");
                case "learning_pace" -> Set.of("slow", "moderate", "fast");
                default -> Set.of();
            };
            if (values.isEmpty()) throw new IllegalArgumentException("preferenceKey is invalid");
            required(preferenceValue, values, "preferenceValue");
        }
        public String eventType() { return "preference_changed"; } public String sourceModule() { return "M6"; }
    }
    private static void identifier(String value, String name) { if (value == null || !IDENTIFIER.matcher(value).matches()) throw new IllegalArgumentException(name + " is invalid"); }
    private static void required(String value, Set<String> values, String name) { if (value == null || !values.contains(value)) throw new IllegalArgumentException(name + " is invalid"); }
    private static void optional(String value, Set<String> values, String name) { if (value != null) required(value, values, name); }
    private static void between(int value, int min, int max, String name) { if (value < min || value > max) throw new IllegalArgumentException(name + " is invalid"); }
    private static void fraction(BigDecimal value, String name) { if (value == null || value.compareTo(BigDecimal.ZERO) < 0 || value.compareTo(BigDecimal.ONE) > 0) throw new IllegalArgumentException(name + " is invalid"); }
}
