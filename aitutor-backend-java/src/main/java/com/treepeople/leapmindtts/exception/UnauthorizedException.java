package com.treepeople.leapmindtts.exception;

/**
 * 未授权异常
 * 当用户没有足够权限访问资源时抛出
 */
public class UnauthorizedException extends RuntimeException {
    
    public UnauthorizedException(String message) {
        super(message);
    }
    
    public UnauthorizedException(String message, Throwable cause) {
        super(message, cause);
    }
}