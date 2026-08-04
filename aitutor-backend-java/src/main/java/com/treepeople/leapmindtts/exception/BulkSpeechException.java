package com.treepeople.leapmindtts.exception;

/**
 * 批量语音合成异常类
 */
public class BulkSpeechException extends RuntimeException {
    
    private final String errorCode;
    private final String courseId;
    private final Integer slidePageNumber;
    private final Integer contentPointIndex;
    
    public BulkSpeechException(String message) {
        super(message);
        this.errorCode = "BULK_SPEECH_ERROR";
        this.courseId = null;
        this.slidePageNumber = null;
        this.contentPointIndex = null;
    }
    
    public BulkSpeechException(String message, Throwable cause) {
        super(message, cause);
        this.errorCode = "BULK_SPEECH_ERROR";
        this.courseId = null;
        this.slidePageNumber = null;
        this.contentPointIndex = null;
    }
    
    public BulkSpeechException(String errorCode, String message, String courseId, 
                              Integer slidePageNumber, Integer contentPointIndex) {
        super(message);
        this.errorCode = errorCode;
        this.courseId = courseId;
        this.slidePageNumber = slidePageNumber;
        this.contentPointIndex = contentPointIndex;
    }
    
    public BulkSpeechException(String errorCode, String message, String courseId, 
                              Integer slidePageNumber, Integer contentPointIndex, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.courseId = courseId;
        this.slidePageNumber = slidePageNumber;
        this.contentPointIndex = contentPointIndex;
    }
    
    public String getErrorCode() {
        return errorCode;
    }
    
    public String getCourseId() {
        return courseId;
    }
    
    public Integer getSlidePageNumber() {
        return slidePageNumber;
    }
    
    public Integer getContentPointIndex() {
        return contentPointIndex;
    }
}