
 package com.treepeople.leapmindtts.service.optimize;
 
 import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service("optimizeRequestMergeService")
public class RequestMergeService {

    // 存储合并请求的池子
    private final ConcurrentHashMap<String, CompletableFuture<String>> pendingRequests = new ConcurrentHashMap<>();

    public RequestMergeResult tryMerge(String key) {
        CompletableFuture<String> newFuture = new CompletableFuture<>();
        
        // putIfAbsent 是原子操作：没有则插入并返回 null；已有则不插入并返回旧值
        CompletableFuture<String> existingFuture = pendingRequests.putIfAbsent(key, newFuture);
        
        if (existingFuture == null) {
            // 返回 null，说明当前线程成功将 newFuture 放进去了，你就是唯一的领头羊（首车）！
            return new RequestMergeResult(true, newFuture);
        } else {
            // 返回不为 null，说明池子里已经有别的线程建立的 Future 了，当前线程作为跟车者等待
            return new RequestMergeResult(false, existingFuture);
        }
    }

    public void completeRequest(String key, String result) {
        // 1. 从池子里取出当时放进去的 Future，并在池子中移除这个 Key（防止内存泄漏和后续同名请求永久被老缓存挡住）
        CompletableFuture<String> future = pendingRequests.remove(key);
        if (future != null) {
            // 2. 唤醒所有挂在 future.get() 上等待的跟车线程，把 AI 答案分发给他们！
            future.complete(result);
        }
    }
 
     public void failRequest(String dedupKey, Throwable ex) {
        CompletableFuture<String> future = pendingRequests.remove(dedupKey);
        if (future != null && !future.isDone()) {
            future.completeExceptionally(ex);
        }
    }
 
     @Data
     @AllArgsConstructor
     public static class RequestMergeResult {
         private boolean isFirst;
         private CompletableFuture<String> future;
         public static RequestMergeResult first(CompletableFuture<String> f) { return new RequestMergeResult(true, f); }
         public static RequestMergeResult merged(CompletableFuture<String> f) { return new RequestMergeResult(false, f); }
     }
 }
