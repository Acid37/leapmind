package com.treepeople.leapmindtts.controller;

import com.treepeople.leapmindtts.pojo.result.ApiResponse;
import com.treepeople.leapmindtts.service.PracticeService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/wrong-questions")
@RequiredArgsConstructor
public class WrongQuestionController {

    private final PracticeService practiceService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> list(
            HttpServletRequest request,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String chapter,
            @RequestParam(required = false) String knowledgePoint) {
        return ResponseEntity.ok(ApiResponse.success(
                practiceService.getMistakes(currentUserId(request), status, chapter, knowledgePoint),
                "查询错题本列表成功"));
    }

    @PutMapping("/{id}/focus")
    public ResponseEntity<ApiResponse<Map<String, Object>>> focus(
            HttpServletRequest request,
            @PathVariable Long id,
            @RequestBody(required = false) FocusRequest body) {
        Boolean focused = body == null ? null : body.getFocused();
        return ResponseEntity.ok(ApiResponse.success(
                practiceService.setMistakeFocus(currentUserId(request), id, focused),
                "重点复习状态已更新"));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(HttpServletRequest request, @PathVariable Long id) {
        practiceService.deleteMistake(currentUserId(request), id);
        return ResponseEntity.ok(ApiResponse.success(null, "错题记录已删除"));
    }

    @PostMapping("/batch-redo")
    public ResponseEntity<ApiResponse<Map<String, Object>>> batchRedo(
            HttpServletRequest request,
            @RequestBody(required = false) BatchRedoRequest body) {
        List<Long> ids = body == null ? List.of() : body.getIds();
        return ResponseEntity.ok(ApiResponse.success(
                practiceService.createMistakeRedoSession(currentUserId(request), ids),
                "错题重做练习会话已生成"));
    }

    private Long currentUserId(HttpServletRequest request) {
        Object userId = request.getAttribute("userId");
        if (userId instanceof Long id) {
            return id;
        }
        throw new IllegalStateException("未获取到登录用户");
    }

    @Data
    public static class FocusRequest {
        private Boolean focused;
    }

    @Data
    public static class BatchRedoRequest {
        private List<Long> ids;
    }
}
