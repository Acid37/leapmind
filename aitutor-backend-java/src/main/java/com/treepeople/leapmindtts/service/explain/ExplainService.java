package com.treepeople.leapmindtts.service.explain;

import com.treepeople.leapmindtts.pojo.dto.AICallRequest;
import com.treepeople.leapmindtts.pojo.dto.GenerateExplainRequest;
import com.treepeople.leapmindtts.pojo.dto.PhotoQARequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 答疑/讲题服务
 * 负责组装结构化参数 → 调 Python AI（PromptManager 构建 Prompt）→ 返回流式结果
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExplainService {

    private final PythonAIClient pythonAIClient;

    /**
     * 拍照答疑 —— 流式 SSE
     */
    public Flux<String> photoQA(PhotoQARequest request) {
        AICallRequest aiRequest = buildAIRequest("explain", "photo_qa", buildPhotoQAParams(request));
        return pythonAIClient.callStream(aiRequest);
    }

    /**
     * 讲题生成 —— 流式 SSE
     */
    public Flux<String> generateExplain(GenerateExplainRequest request) {
        AICallRequest aiRequest = buildAIRequest("explain", "explain_wrong", buildExplainParams(request));
        return pythonAIClient.callStream(aiRequest);
    }

    /**
     * 生成唯一的会话 ID
     */
    public String generateId() {
        return "explain_" + UUID.randomUUID().toString().substring(0, 8);
    }

    // ─── 结构化参数构建（传给 Python PromptManager）───

    private Map<String, Object> buildPhotoQAParams(PhotoQARequest request) {
        Map<String, Object> question = new HashMap<>();
        question.put("stem", request.getQuestion().getStem());
        question.put("options", request.getQuestion().getOptions());
        question.put("subject", request.getQuestion().getSubject());
        question.put("type", request.getQuestion().getType());

        Map<String, Object> params = new HashMap<>();
        params.put("question", question);

        if (request.getUserProfile() != null) {
            Map<String, Object> userProfile = new HashMap<>();
            userProfile.put("grade", request.getUserProfile().getGrade());
            userProfile.put("weakPoints", request.getUserProfile().getWeakPoints());
            params.put("userProfile", userProfile);
        }
        return params;
    }

    private Map<String, Object> buildExplainParams(GenerateExplainRequest request) {
        Map<String, Object> question = new HashMap<>();
        question.put("stem", request.getQuestionStem());
        question.put("correctAnswer", request.getCorrectAnswer());

        Map<String, Object> userAnswer = new HashMap<>();
        userAnswer.put("selected", request.getUserAnswer());

        Map<String, Object> params = new HashMap<>();
        params.put("question", question);
        params.put("userAnswer", userAnswer);
        params.put("wrongReasonTag", request.getWrongReasonTag());
        params.put("knowledgePoints", request.getKnowledgePoints());
        return params;
    }

    private AICallRequest buildAIRequest(String module, String scene, Map<String, Object> params) {
        AICallRequest req = new AICallRequest();
        req.setModule(module);
        req.setScene(scene);
        req.setParams(params);
        return req;
    }
}
