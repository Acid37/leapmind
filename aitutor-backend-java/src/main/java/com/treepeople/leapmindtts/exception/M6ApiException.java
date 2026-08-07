package com.treepeople.leapmindtts.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;
import java.util.List;
import com.treepeople.leapmindtts.pojo.dto.profile.M6Dtos.FieldViolation;

@Getter
public class M6ApiException extends RuntimeException {
    private final HttpStatus status;
    private final String errorCode;
    private final List<FieldViolation> details;

    public M6ApiException(HttpStatus status, String errorCode, String message) {
        this(status, errorCode, message, List.of());
    }

    public M6ApiException(HttpStatus status, String errorCode, String message, List<FieldViolation> details) {
        super(message);
        this.status = status;
        this.errorCode = errorCode;
        this.details = List.copyOf(details);
    }
}
