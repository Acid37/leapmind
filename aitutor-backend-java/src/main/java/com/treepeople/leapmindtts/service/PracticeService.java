package com.treepeople.leapmindtts.service;

import com.treepeople.leapmindtts.controller.PracticeController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

public interface PracticeService {
    Map<String, Object> getFilters();
    Map<String, Object> listQuestions(Map<String, Object> params);
    Map<String, Object> getQuestionDetail(Long questionId);
    Map<String, Object> createQuestion(PracticeController.QuestionRequest request);
    Map<String, Object> updateQuestion(Long questionId, PracticeController.QuestionRequest request);
    void deleteQuestion(Long questionId);
    Map<String, Object> importQuestions(MultipartFile file);
    Map<String, Object> getNextQuestion(Long userId, String mode, String track, String chapter, String knowledgePoint);
    Map<String, Object> getNextQuestion(Long userId, String mode, String subject, String gradeLevel, String track, String chapter, String knowledgePoint, String questionType, String difficulty, String lessonId);
    Map<String, Object> submitAnswer(Long userId, PracticeController.SubmitAnswerRequest request);
    List<Map<String, Object>> getRecords(Long userId, String range, String chapter, String knowledgePoint, Boolean wrongOnly);
    List<Map<String, Object>> getMistakes(Long userId, String status, String chapter, String knowledgePoint);
    Map<String, Object> setMistakeFocus(Long userId, Long mistakeId, Boolean focused);
    void deleteMistake(Long userId, Long mistakeId);
    Map<String, Object> createMistakeRedoSession(Long userId, List<Long> mistakeIds);
    void updateMistakeStatus(Long userId, Long mistakeId, PracticeController.UpdateMistakeRequest request);
    Map<String, Object> getDashboard(Long userId);
    Map<String, Object> getStatistics(Long userId, String range);
    Map<String, Object> getLeaderboards(Long userId, String track);
    Map<String, Object> checkin(Long userId);
    Map<String, Object> getCheckinStatus(Long userId);
    void updatePrivacy(Long userId, boolean hidden);
    void updateMistake(Long userId, Long recordId, PracticeController.UpdateMistakeRequest request);
    String exportRecords(Long userId, boolean wrongOnly);
    void clearUserData(Long userId);
}
