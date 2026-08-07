
 package com.treepeople.leapmindtts.exception;
 
 import lombok.Getter;
 
 @Getter
 public enum BizErrorCode {
     RATE_LIMITED(1001, "您提问的频率有点快，请稍等片刻后再试。"),
     QUESTION_DUPLICATE(1002, "您提问的问题正在拼命思考中，请稍候..."),
     SERVICE_DEGRADED(1003, "服务暂时降级，请稍后重新尝试。"),
     AI_TIMEOUT(2001, "AI 服务思考时间过长，已主动终止连接，请重试。");
 
     private final int code;
     private final String message;
 
     BizErrorCode(int code, String message) {
         this.code = code;
         this.message = message;
     }
 }
 