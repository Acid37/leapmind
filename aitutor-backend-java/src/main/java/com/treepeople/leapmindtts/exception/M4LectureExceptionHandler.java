package com.treepeople.leapmindtts.exception;

import com.treepeople.leapmindtts.pojo.result.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * M4 即时讲课异常处理器
 * <p>
 * 将 {@link M4LectureException} 转换为对应 HTTP 状态码 + {@link ApiResponse}。
 * 仅作用于 M4 讲课 Controller，避免影响全局异常处理策略。
 */
@Slf4j
@RestControllerAdvice
public class M4LectureExceptionHandler {

    @ExceptionHandler(M4LectureException.class)
    public ResponseEntity<ApiResponse<Void>> handleM4LectureException(M4LectureException e) {
        log.warn("[M4] 讲课业务异常: status={}, message={}", e.getStatus(), e.getMessage());
        return ResponseEntity.status(e.getStatus())
                .body(ApiResponse.error(e.getStatus().value(), e.getMessage()));
    }
}
