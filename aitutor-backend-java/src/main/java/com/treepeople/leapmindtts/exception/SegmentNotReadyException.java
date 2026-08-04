package com.treepeople.leapmindtts.exception;

/**
 * 片段未准备就绪异常
 * 当请求的语音片段尚未完成合成时抛出此异常
 */
public class SegmentNotReadyException extends RuntimeException {
    
    public SegmentNotReadyException(String message) {
        super(message);
    }
    
    public SegmentNotReadyException(String message, Throwable cause) {
        super(message, cause);
    }
}