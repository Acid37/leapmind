package com.treepeople.leapmindtts.service.question;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.treepeople.leapmindtts.mapper.QuestionMapper;
import com.treepeople.leapmindtts.pojo.dto.QuestionMatchRequest;
import com.treepeople.leapmindtts.pojo.vo.QuestionMatchResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 题库相似题匹配服务
 *
 * 三级匹配策略：
 * - matchDegree > 0.9 → 直接复用已有解析，减少 AI 调用
 * - matchDegree 0.6-0.9 → 返回候选列表给用户确认
 * - matchDegree < 0.6 → 走 AI 生成
 *
 * TODO: M1 建好 Question 实体后，把 Map<String,Object> 改回 Question 类型
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QuestionMatchService {

    private final QuestionMapper questionMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final double REUSE_THRESHOLD = 0.9;
    private static final double CANDIDATE_THRESHOLD = 0.6;

    public QuestionMatchResult matchQuestion(QuestionMatchRequest request) {
        String stem = request.getStem();
        String subject = request.getSubject();
        log.info("题库匹配: stem='{}...', subject={}",
                stem.substring(0, Math.min(50, stem.length())), subject);

        String keyword = extractKeywords(stem);

        List<Map<String, Object>> candidates = questionMapper.fulltextSearch(keyword, subject, 10);
        if (candidates.isEmpty()) {
            log.info("全文索引无结果，降级为 LIKE 搜索");
            candidates = questionMapper.likeSearch(keyword, subject, 10);
        }

        if (candidates.isEmpty()) {
            log.info("未找到匹配题目，走 AI 生成");
            return QuestionMatchResult.builder().matched(false).matchDegree(0.0).build();
        }

        Map<String, Object> bestMatch = candidates.get(0);
        String contentJson = (String) bestMatch.get("content_json");
        double similarity = calculateSimilarity(stem, contentJson);
        Long questionId = (Long) bestMatch.get("id");

        log.info("最佳匹配: questionId={}, similarity={}", questionId, similarity);

        if (similarity >= REUSE_THRESHOLD) {
            return QuestionMatchResult.builder()
                    .matched(true).matchDegree(similarity).questionId(questionId)
                    .existingExplanation(bestMatch.get("answer_json"))
                    .build();
        } else if (similarity >= CANDIDATE_THRESHOLD) {
            return QuestionMatchResult.builder()
                    .matched(true).matchDegree(similarity).questionId(questionId)
                    .candidates(buildCandidateList(candidates, stem))
                    .build();
        } else {
            return QuestionMatchResult.builder().matched(false).matchDegree(similarity).build();
        }
    }

    private String extractKeywords(String stem) {
        return stem.replaceAll("[，。！？；：、\"'【】（）\\s]", " ").trim();
    }

    private double calculateSimilarity(String stem, String contentJson) {
        if (contentJson == null || contentJson.isEmpty()) return 0.0;
        String textA = normalize(stem);
        String textB = normalize(extractStemFromJson(contentJson));
        if (textA.isEmpty() || textB.isEmpty()) return 0.0;
        int commonChars = 0;
        for (char c : textA.toCharArray()) {
            if (textB.indexOf(c) >= 0) commonChars++;
        }
        return (double) commonChars / Math.max(textA.length(), textB.length());
    }

    private String normalize(String text) {
        return text.replaceAll("[^a-zA-Z0-9\\u4e00-\\u9fa5]", "").toLowerCase();
    }

    private String extractStemFromJson(String contentJson) {
        try {
            var node = objectMapper.readTree(contentJson);
            if (node.has("stem")) return node.get("stem").asText();
            return contentJson;
        } catch (Exception e) {
            return contentJson;
        }
    }

    private List<QuestionMatchResult.CandidateQuestion> buildCandidateList(
            List<Map<String, Object>> questions, String stem) {
        List<QuestionMatchResult.CandidateQuestion> list = new ArrayList<>();
        for (int i = 1; i < Math.min(questions.size(), 5); i++) {
            Map<String, Object> q = questions.get(i);
            String cjson = (String) q.get("content_json");
            list.add(QuestionMatchResult.CandidateQuestion.builder()
                    .questionId((Long) q.get("id"))
                    .stemSnippet(extractStemFromJson(cjson))
                    .similarity(calculateSimilarity(stem, cjson))
                    .build());
        }
        return list;
    }
}

