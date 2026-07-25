package com.treepeople.leapmindtts.controller.question;

import com.treepeople.leapmindtts.pojo.dto.QuestionMatchRequest;
import com.treepeople.leapmindtts.pojo.result.ApiResponse;
import com.treepeople.leapmindtts.pojo.vo.QuestionMatchResult;
import com.treepeople.leapmindtts.service.question.QuestionMatchService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 题库相似题匹配控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/questions")
@RequiredArgsConstructor
public class QuestionMatchController {

    private final QuestionMatchService questionMatchService;

    /**
     * 题库相似题匹配
     *
     * 三级匹配策略：
     * - matchDegree > 0.9 → 直接复用已有解析
     * - matchDegree 0.6-0.9 → 返回候选列表
     * - matchDegree < 0.6 → 返回 unmatched，前端走 AI 生成
     */
    @PostMapping("/match")
    public ResponseEntity<ApiResponse<QuestionMatchResult>> matchQuestion(
            @RequestBody @Valid QuestionMatchRequest request) {
        log.info("题库匹配请求: subject={}", request.getSubject());
        try {
            QuestionMatchResult result = questionMatchService.matchQuestion(request);
            return ResponseEntity.ok(ApiResponse.success(result, "匹配完成"));
        } catch (Exception e) {
            log.error("题库匹配失败", e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error(500, "匹配服务异常：" + e.getMessage()));
        }
    }
}
