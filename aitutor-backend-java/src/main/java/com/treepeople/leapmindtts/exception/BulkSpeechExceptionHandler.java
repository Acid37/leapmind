package com.treepeople.leapmindtts.exception;

import com.treepeople.leapmindtts.pojo.dto.BulkSynthesisResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import java.time.LocalDateTime;
import java.util.stream.Collectors;

/**
 * 批量语音合成异常处理器
 */
@RestControllerAdvice
@Slf4j
public class BulkSpeechExceptionHandler {
    
    /**
     * 处理批量语音合成异常
     */
    @ExceptionHandler(BulkSpeechException.class)
    public ResponseEntity<BulkSynthesisResponse> handleBulkSpeechException(BulkSpeechException e) {
        log.error("批量语音合成异常，错误码: {}, 会话ID: {}, 页码: {}, 内容点: {}", 
                e.getErrorCode(), e.getCourseId(), e.getSlidePageNumber(), e.getContentPointIndex(), e);
        
        BulkSynthesisResponse response = BulkSynthesisResponse.builder()
                .courseId(e.getCourseId())
                .status("FAILED")
                .message(e.getMessage())
                .startTime(LocalDateTime.now())
                .build();
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }
    
    /**
     * 处理参数验证异常
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<BulkSynthesisResponse> handleValidationException(MethodArgumentNotValidException e) {
        String errorMessage = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining(", "));
        
        log.warn("请求参数验证失败: {}", errorMessage);
        
        BulkSynthesisResponse response = BulkSynthesisResponse.builder()
                .status("FAILED")
                .message("参数验证失败: " + errorMessage)
                .startTime(LocalDateTime.now())
                .build();
        
        return ResponseEntity.badRequest().body(response);
    }
    
    /**
     * 处理绑定异常
     */
    @ExceptionHandler(BindException.class)
    public ResponseEntity<BulkSynthesisResponse> handleBindException(BindException e) {
        String errorMessage = e.getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining(", "));
        
        log.warn("参数绑定失败: {}", errorMessage);
        
        BulkSynthesisResponse response = BulkSynthesisResponse.builder()
                .status("FAILED")
                .message("参数绑定失败: " + errorMessage)
                .startTime(LocalDateTime.now())
                .build();
        
        return ResponseEntity.badRequest().body(response);
    }
    
    /**
     * 处理约束违反异常
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<BulkSynthesisResponse> handleConstraintViolationException(ConstraintViolationException e) {
        String errorMessage = e.getConstraintViolations().stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.joining(", "));
        
        log.warn("约束验证失败: {}", errorMessage);
        
        BulkSynthesisResponse response = BulkSynthesisResponse.builder()
                .status("FAILED")
                .message("约束验证失败: " + errorMessage)
                .startTime(LocalDateTime.now())
                .build();
        
        return ResponseEntity.badRequest().body(response);
    }
    
    /**
     * 处理非法参数异常
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<BulkSynthesisResponse> handleIllegalArgumentException(IllegalArgumentException e) {
        log.warn("非法参数异常: {}", e.getMessage());
        
        BulkSynthesisResponse response = BulkSynthesisResponse.builder()
                .status("FAILED")
                .message("参数错误: " + e.getMessage())
                .startTime(LocalDateTime.now())
                .build();
        
        return ResponseEntity.badRequest().body(response);
    }
    
    /**
     * 处理通用异常
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<BulkSynthesisResponse> handleGenericException(Exception e) {
        log.error("未处理的异常", e);
        
        BulkSynthesisResponse response = BulkSynthesisResponse.builder()
                .status("FAILED")
                .message("系统内部错误，请稍后重试")
                .startTime(LocalDateTime.now())
                .build();
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }
}