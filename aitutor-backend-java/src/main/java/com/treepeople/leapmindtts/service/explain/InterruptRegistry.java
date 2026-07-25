package com.treepeople.leapmindtts.service.explain;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.Disposable;
import reactor.core.publisher.FluxSink;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 打断注册中心
 * 管理所有活跃的 SSE 流式订阅，支持前端发起打断请求时取消对应的 AI 生成。
 *
 * 设计思路：
 * - 每次 SSE 流式请求开始时注册（generateId → subscription）
 * - 前端发 POST /api/explain/interrupt?generateId=xxx 时取消订阅
 * - 流正常结束或异常时自动清理注册
 */
@Slf4j
@Component
public class InterruptRegistry {

    /** generateId → 活跃的 SSE 订阅 */
    private final ConcurrentHashMap<String, SubscriptionEntry> activeStreams = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 注册一个活跃的流式会话
     *
     * @param generateId  生成会话 ID
     * @param disposable  可取消的订阅
     * @param sink        SSE sink（用于发送 interrupt 确认事件）
     */
    public void register(String generateId, Disposable disposable, FluxSink<String> sink) {
        activeStreams.put(generateId, new SubscriptionEntry(disposable, sink));
        log.info("注册流式会话: generateId={}, 当前活跃数={}", generateId, activeStreams.size());
    }

    /**
     * 打断指定的流式会话
     *
     * @param generateId 生成会话 ID
     * @return 是否成功打断
     */
    public boolean interrupt(String generateId) {
        SubscriptionEntry entry = activeStreams.remove(generateId);
        if (entry == null) {
            log.warn("打断失败：会话不存在或已结束, generateId={}", generateId);
            return false;
        }

        // 1. 先取消上游订阅（停止从 Python 拉流）
        entry.disposable.dispose();

        // 2. 再发 interrupt 确认事件 + 关闭下游
        try {
            String interruptJson = objectMapper.writeValueAsString(
                    Map.of("type", "interrupt", "message", "已打断生成"));
            entry.sink.next(interruptJson);
            entry.sink.complete();
        } catch (Exception e) {
            log.debug("发送 interrupt 事件时 sink 已关闭: {}", e.getMessage());
        }

        log.info("已打断流式会话: generateId={}, 剩余活跃数={}", generateId, activeStreams.size());
        return true;
    }

    /**
     * 会话正常结束时清理
     */
    public void unregister(String generateId) {
        SubscriptionEntry removed = activeStreams.remove(generateId);
        if (removed != null) {
            log.debug("注销流式会话: generateId={}", generateId);
        }
    }

    /**
     * 获取当前活跃会话数
     */
    public int getActiveCount() {
        return activeStreams.size();
    }

    /** 内部类：订阅条目 */
    private record SubscriptionEntry(Disposable disposable, FluxSink<String> sink) {}
}
