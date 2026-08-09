package com.treepeople.leapmindtts.exception;

import com.treepeople.leapmindtts.controller.EventCollectionController;
import com.treepeople.leapmindtts.controller.user.UserProfileController;
import com.treepeople.leapmindtts.pojo.result.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataAccessException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Keeps legacy route bodies stable while making denied cross-user access an actual 401/403 response. */
@RestControllerAdvice(assignableTypes = {UserProfileController.class, EventCollectionController.class})
@Order(Ordered.HIGHEST_PRECEDENCE)
public class LegacyProfileSecurityExceptionHandler {
    @ExceptionHandler(M6ApiException.class)
    ResponseEntity<ApiResponse<Object>> access(M6ApiException exception) {
        return ResponseEntity.status(exception.getStatus()).body(ApiResponse.error(exception.getStatus().value(), exception.getMessage()));
    }
    @ExceptionHandler(DataAccessException.class)
    ResponseEntity<ApiResponse<Object>> database(DataAccessException exception) {
        return ResponseEntity.status(503).body(ApiResponse.error(503, "Service unavailable"));
    }
}
