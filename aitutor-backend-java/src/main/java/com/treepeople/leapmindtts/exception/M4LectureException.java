package com.treepeople.leapmindtts.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * M4 即时讲课业务异常
 * <p>
 * 携带 HTTP 状态码，由 {@link M4LectureExceptionHandler} 统一转换为
 * {@code ApiResponse<T>} 返回给前端。
 */
@Getter
public class M4LectureException extends RuntimeException {

    private final HttpStatus status;

    public M4LectureException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public M4LectureException(HttpStatus status, String message, Throwable cause) {
        super(message, cause);
        this.status = status;
    }

    /** 讲课内容不存在 → HTTP 404 */
    public static M4LectureException notFound(String courseId) {
        return new M4LectureException(HttpStatus.NOT_FOUND,
                "讲课内容不存在: courseId=" + courseId);
    }

    /** 请求参数不合法 → HTTP 400 */
    public static M4LectureException badRequest(String message) {
        return new M4LectureException(HttpStatus.BAD_REQUEST, message);
    }
}
