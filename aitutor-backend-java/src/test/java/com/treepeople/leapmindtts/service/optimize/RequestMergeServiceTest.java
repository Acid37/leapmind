
 package com.treepeople.leapmindtts.service.optimize;
 
 import com.treepeople.leapmindtts.BaseIntegrationTest;
 import lombok.extern.slf4j.Slf4j;
 import org.junit.jupiter.api.Test;
 import org.springframework.beans.factory.annotation.Autowired;

 import java.util.List;
 import java.util.concurrent.*;
 import java.util.concurrent.atomic.AtomicInteger;

 import static org.junit.jupiter.api.Assertions.assertEquals;
 import static org.junit.jupiter.api.Assertions.assertTrue;
 
 @Slf4j
 class RequestMergeServiceTest extends BaseIntegrationTest {
 
     @Autowired
     private RequestMergeService requestMergeService;
 
     @Test
     void testConcurrentIdenticalRequestsOnlyExecuteOnce() throws InterruptedException {
         int threadCount = 10;
         String dedupKey = "user100:why_sky_is_blue";
         
         // 使用 CountDownLatch 确保 10 个线程在同一时刻发车（模拟真实高并发瞬间）
         CountDownLatch startLatch = new CountDownLatch(1);
         CountDownLatch finishLatch = new CountDownLatch(threadCount);
         ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
         
         // 用于记录标记为 "isFirst=true" 的线程数量
         AtomicInteger firstRequestCount = new AtomicInteger(0);
         // 用于收集所有线程最终拿到的结果
         List<String> results = new CopyOnWriteArrayList<>();
 
         for (int i = 0; i < threadCount; i++) {
             executorService.submit(() -> {
                 try {
                     // 等待主线程放行信号
                     startLatch.await();
                     
                     // 发起尝试合并
                     RequestMergeService.RequestMergeResult mergeResult = requestMergeService.tryMerge(dedupKey);
                     
                     if (mergeResult.isFirst()) {
                         firstRequestCount.incrementAndGet();
                         // 模拟 AI 耗时思考 150ms
                         Thread.sleep(150);
                         String aiAnswer = "The sky is blue due to Rayleigh scattering.";
                         // 通知合并池中的其他等待线程
                         requestMergeService.completeRequest(dedupKey, aiAnswer);
                         results.add(aiAnswer);
                     } else {
                         // 后续重复请求：挂起等待首个请求的结果（设置 2 秒超时防止死锁）
                         String mergedAnswer = mergeResult.getFuture().get(2, TimeUnit.SECONDS);
                         results.add(mergedAnswer);
                     }
                 } catch (Exception e) {
                     log.error("并发线程发生异常", e);
                 } finally {
                     finishLatch.countDown();
                 }
             });
         }
 
         // 触发并发发车！
         startLatch.countDown();
         
         // 等待所有线程执行结束，最多等待 5 秒
         boolean allFinished = finishLatch.await(5, TimeUnit.SECONDS);
         assertTrue(allFinished, "所有线程必须在超时前完成计算");
 
         // --- 核心断言 ---
         // 1. 10 个线程并发下，有且仅有 1 个线程被认定为首个请求
         assertEquals(1, firstRequestCount.get(), "AI 核心耗时逻辑应当只被真正调起 1 次");
         // 2. 所有 10 个线程都必须拿到结果
         assertEquals(threadCount, results.size(), "所有线程都必须顺利获取到答案");
         // 3. 所有线程拿到的答案内容完全一致
         assertTrue(results.stream().allMatch("The sky is blue due to Rayleigh scattering."::equals), "所有线程的计算结果必须完全一致");
         
         executorService.shutdown();
     }
 }
 