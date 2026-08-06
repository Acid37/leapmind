package com.treepeople.leapmindtts.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * @ Author：YangYu
 * @ Package：com.treepeople.leapmindtts.config
 * @ Project：leapmind-tts
 * @ Description:
 * @ Date：2025/7/15  11:46
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean(name = "taskExecutor")
    @Primary
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(10);
        executor.setMaxPoolSize(20);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("baidu-audio-processor-");

        executor.setAwaitTerminationSeconds(60);
        executor.setWaitForTasksToCompleteOnShutdown(true);

        executor.initialize();
        return executor;
    }
    /**
     * 新增：备课批量TTS旁白配音专用线程池
     * 固定3核心/最大线程，队列30，专门承载TtsPreGenerateService异步配音任务
     * 拒绝策略：调用线程执行，避免配音任务丢失
     */
    @Bean(name = "ttsTaskExecutor")
    public Executor ttsTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        // 固定3条线程处理配音任务，控制并发，防止大量备课同时生成音频压垮接口
        executor.setCorePoolSize(3);
        executor.setMaxPoolSize(3);
        executor.setQueueCapacity(30);
        executor.setThreadNamePrefix("tts-task-");
        // 队列满时，由发起请求的主线程执行任务，不丢弃旁白生成任务
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        // 服务关闭时等待未完成配音任务执行完毕
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);

        executor.initialize();
        return executor;
    }
    
    /**
     * [SSE流式] 备课生成流式透传专用线程池。
     * 用途：承载 Python → Java → 前端 的 SSE 流式处理后台线程（每个请求占用1条线程读 WebClient 流并写入 SseEmitter）。
     * 设计：核心5/最大20，队列80；拒绝策略 CallerRunsPolicy 避免流式请求被丢弃。
     */
    @Bean(name = "sseStreamExecutor")
    public Executor sseStreamExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5);
        executor.setMaxPoolSize(20);
        executor.setQueueCapacity(80);
        executor.setThreadNamePrefix("sse-stream-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(120);
        executor.initialize();
        return executor;
    }

}
