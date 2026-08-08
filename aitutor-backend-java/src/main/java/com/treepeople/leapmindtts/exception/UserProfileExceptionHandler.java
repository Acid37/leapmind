package com.treepeople.leapmindtts.exception;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.treepeople.leapmindtts.controller.user.M6ContextController;
import com.treepeople.leapmindtts.pojo.dto.profile.M6Dtos.ErrorData;
import com.treepeople.leapmindtts.pojo.dto.profile.M6Dtos.FieldViolation;
import com.treepeople.leapmindtts.pojo.result.ApiResponse;
import com.treepeople.leapmindtts.util.M6RequestIds;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.util.List;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = M6ContextController.class)
public class UserProfileExceptionHandler {
    private static final Logger LOG = LoggerFactory.getLogger(UserProfileExceptionHandler.class);
    @ExceptionHandler(M6ApiException.class)
    ResponseEntity<ApiResponse<ErrorData>> m6(M6ApiException e, HttpServletRequest request) {
        return response(e.getStatus(), e.getErrorCode(), e.getMessage(), e.getDetails(), request);
    }
    @ExceptionHandler(DataAccessException.class)
    ResponseEntity<ApiResponse<ErrorData>> database(Exception e, HttpServletRequest request) {
        return response(HttpStatus.SERVICE_UNAVAILABLE, "PROFILE_SERVICE_DEGRADED", "用户画像服务暂不可用", List.of(), request);
    }
    @ExceptionHandler({HttpMessageNotReadableException.class, JsonProcessingException.class,
            ConstraintViolationException.class, MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class})
    ResponseEntity<ApiResponse<ErrorData>> input(Exception e, HttpServletRequest request) {
        return response(HttpStatus.BAD_REQUEST, "PROFILE_EVENT_INVALID", "请求格式无效", List.of(), request);
    }
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    ResponseEntity<ApiResponse<ErrorData>> unsupportedMediaType(HttpMediaTypeNotSupportedException e, HttpServletRequest request) {
        return response(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "PROFILE_EVENT_INVALID", "Unsupported request media type", List.of(), request);
    }
    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiResponse<ErrorData>> unexpected(Exception e, HttpServletRequest request) {
        LOG.warn("M6 unexpected requestId={} operation={} exceptionType={}", M6RequestIds.resolveOrCreate(request),
                operation(request), e.getClass().getSimpleName());
        return response(HttpStatus.INTERNAL_SERVER_ERROR, "PROFILE_INTERNAL_ERROR", "系统内部错误", List.of(), request);
    }
    private ResponseEntity<ApiResponse<ErrorData>> response(HttpStatus status, String errorCode, String message,
                                                             List<FieldViolation> details, HttpServletRequest request) {
        String requestId = M6RequestIds.resolveOrCreate(request);
        ApiResponse<ErrorData> body = ApiResponse.<ErrorData>builder().code(status.value()).message(message)
                .data(new ErrorData(requestId, errorCode, details)).timestamp(System.currentTimeMillis()).build();
        return ResponseEntity.status(status).header("X-Request-Id", requestId).body(body);
    }
    private String operation(HttpServletRequest request) {
        String uri = request.getRequestURI();
        if (uri.endsWith("/record-event")) return "RECORD_EVENT";
        if (uri.endsWith("/batch-events")) return "BATCH_EVENTS";
        if (uri.endsWith("/summary")) return "PROFILE_SUMMARY";
        if (uri.endsWith("/knowledge-status")) return "KNOWLEDGE_STATUS";
        return "PROFILE_READ";
    }
}
