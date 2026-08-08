package com.treepeople.leapmindtts.pojo.result;

 import lombok.Data;
 import lombok.AllArgsConstructor;
 import lombok.Builder;
 import lombok.NoArgsConstructor;

 /**
  * 统一API响应格式
  *
  * @param <T> 响应数据类型
  */
 @Data
 @Builder
 @AllArgsConstructor
 @NoArgsConstructor
 public class ApiResponse<T> {
     private int code;
     private String message;
     private T data;
     private long timestamp;

     public static <T> ApiResponse<T> success(T data) {
         return new ApiResponse<>(200, "success", data, System.currentTimeMillis());
     }

     public static <T> ApiResponse<T> error(int code, String message) {
         return new ApiResponse<>(code, message, null, System.currentTimeMillis());
     }

    public static <T> ApiResponse<T> success(T data, String message) {
        return new ApiResponse<>(200, message, data, System.currentTimeMillis());
    }

     // 打开 ApiResponse.java，追加下面这个方法：
     public static <T> ApiResponse<T> error(String message) {
         // 默认给一个 400 的错误状态码
         return new ApiResponse<>(400, message, null, System.currentTimeMillis());
     }
 }
