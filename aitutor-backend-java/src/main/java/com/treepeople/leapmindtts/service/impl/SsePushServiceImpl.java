package com.treepeople.leapmindtts.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Service
public class SsePushServiceImpl {

    private final ObjectMapper objectMapper;

    private final Map<String, SseEmitter> connections = new ConcurrentHashMap<>();
    private final Map<String, ConnectionInfo> connectionInfos = new ConcurrentHashMap<>();
    private final List<ConnectionEventListener> eventListeners = new CopyOnWriteArrayList<>();

    private final ScheduledExecutorService heartbeatScheduler;
    private final ScheduledExecutorService cleanupScheduler;
    private final AtomicBoolean running = new AtomicBoolean(true);

    private static final long DEFAULT_TIMEOUT = 30 * 60 * 1000L;
    private static final long HEARTBEAT_INTERVAL_MS = 30 * 1000L;
    private static final long CLEANUP_INTERVAL_MS = 60 * 1000L;

    public SsePushServiceImpl(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.heartbeatScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "sse-heartbeat");
            t.setDaemon(true);
            return t;
        });
        this.cleanupScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "sse-cleanup");
            t.setDaemon(true);
            return t;
        });

        startHeartbeat();
        startCleanup();
    }

    private void startHeartbeat() {
        heartbeatScheduler.scheduleAtFixedRate(() -> {
            if (running.get()) {
                try {
                    log.debug("开始发送心跳，当前连接数: {}", connections.size());
                    List<String> disconnectedConnections = new ArrayList<>();
                    
                    for (Map.Entry<String, SseEmitter> entry : connections.entrySet()) {
                        try {
                            entry.getValue().send(SseEmitter.event()
                                    .name("heartbeat")
                                    .data("{\"timestamp\":" + System.currentTimeMillis() + "}"));
                            
                            ConnectionInfo info = connectionInfos.get(entry.getKey());
                            if (info != null) {
                                info.setLastActivityTime(System.currentTimeMillis());
                            }
                        } catch (IOException e) {
                            log.warn("发送心跳失败，连接ID: {}, 将标记为断开", entry.getKey());
                            disconnectedConnections.add(entry.getKey());
                        }
                    }
                    
                    for (String connId : disconnectedConnections) {
                        handleDisconnection(connId, "HEARTBEAT_FAILED");
                    }
                    
                } catch (Exception e) {
                    log.error("心跳任务执行异常", e);
                }
            }
        }, HEARTBEAT_INTERVAL_MS, HEARTBEAT_INTERVAL_MS, TimeUnit.MILLISECONDS);
    }

    private void startCleanup() {
        cleanupScheduler.scheduleAtFixedRate(() -> {
            if (running.get()) {
                cleanupExpiredConnections();
            }
        }, CLEANUP_INTERVAL_MS, CLEANUP_INTERVAL_MS, TimeUnit.MILLISECONDS);
    }

    public SseEmitter registerConnection(String connectionId) {
        return registerConnection(connectionId, DEFAULT_TIMEOUT);
    }

    public SseEmitter registerConnection(String connectionId, long timeout) {
        log.info("注册SSE连接，连接ID: {}, 超时: {}ms", connectionId, timeout);

        SseEmitter oldEmitter = connections.remove(connectionId);
        if (oldEmitter != null) {
            try {
                oldEmitter.complete();
                log.info("已关闭旧连接，连接ID: {}", connectionId);
            } catch (Exception e) {
                log.warn("关闭旧连接时出错", e);
            }
        }

        SseEmitter emitter = new SseEmitter(timeout);
        ConnectionInfo info = new ConnectionInfo(connectionId);
        connectionInfos.put(connectionId, info);

        emitter.onTimeout(() -> {
            log.warn("SSE连接超时，连接ID: {}", connectionId);
            ConnectionInfo connInfo = connectionInfos.get(connectionId);
            if (connInfo != null) {
                connInfo.setStatus("TIMEOUT");
            }
            handleDisconnection(connectionId, "TIMEOUT");
        });

        emitter.onError(e -> {
            log.error("SSE连接错误，连接ID: {}", connectionId, e);
            ConnectionInfo connInfo = connectionInfos.get(connectionId);
            if (connInfo != null) {
                connInfo.setStatus("ERROR");
            }
            notifyEvent(connectionId, ConnectionEvent.ERROR, e.getMessage());
            handleDisconnection(connectionId, "ERROR_OCCURRED");
        });

        emitter.onCompletion(() -> {
            log.info("SSE连接完成，连接ID: {}", connectionId);
            ConnectionInfo connInfo = connectionInfos.get(connectionId);
            if (connInfo != null) {
                connInfo.setStatus("COMPLETED");
            }
            handleDisconnection(connectionId, "COMPLETED");
        });

        connections.put(connectionId, emitter);

        try {
            emitter.send(SseEmitter.event()
                    .name("connected")
                    .data(buildMessage("CONFIGURED", "连接已建立", null))
                    .id(connectionId));
            
            info.setLastMessageTime(System.currentTimeMillis());
            info.incrementMessageCount();
            
            notifyEvent(connectionId, ConnectionEvent.CONNECTED, info);
            
        } catch (IOException e) {
            log.error("发送连接成功消息失败", e);
            connections.remove(connectionId);
            connectionInfos.remove(connectionId);
            notifyEvent(connectionId, ConnectionEvent.ERROR, "Failed to send initial message");
            throw new RuntimeException("建立连接失败: " + e.getMessage(), e);
        }

        log.info("SSE连接注册成功，连接ID: {}", connectionId);
        return emitter;
    }

    public void sendEvent(String connectionId, String eventName, Object data) {
        SseEmitter emitter = connections.get(connectionId);
        if (emitter == null) {
            log.warn("连接不存在，无法发送事件，连接ID: {}, 事件名: {}", connectionId, eventName);
            return;
        }

        try {
            String jsonData = convertToJson(data);
            emitter.send(SseEmitter.event()
                    .name(eventName)
                    .data(jsonData));
            
            ConnectionInfo info = connectionInfos.get(connectionId);
            if (info != null) {
                info.setLastActivityTime(System.currentTimeMillis());
                info.setLastMessageTime(System.currentTimeMillis());
                info.incrementMessageCount();
            }
            
            notifyEvent(connectionId, ConnectionEvent.MESSAGE_SENT, eventName);
            log.debug("事件发送成功，连接ID: {}, 事件名: {}", connectionId, eventName);
            
        } catch (IOException e) {
            log.error("发送事件失败，连接ID: {}, 事件名: {}", connectionId, eventName, e);
            handleDisconnection(connectionId, "SEND_FAILED");
        }
    }

    public void sendMessage(String connectionId, Object data) {
        sendEvent(connectionId, "message", data);
    }

    public void sendProgress(String connectionId, int progress, String message) {
        sendProgress(connectionId, progress, "PROCESSING", message);
    }

    public void sendProgress(String connectionId, int progress, String status, String message) {
        progress = Math.max(0, Math.min(100, progress));

        Map<String, Object> progressData = new HashMap<>();
        progressData.put("progress", progress);
        progressData.put("status", status);
        progressData.put("message", message);
        progressData.put("timestamp", System.currentTimeMillis());

        sendEvent(connectionId, "progress", progressData);
        log.debug("进度推送，连接ID: {}, 进度: {}, 状态: {}", connectionId, progress, status);
    }

    public void sendComplete(String connectionId, Object result) {
        Map<String, Object> completeData = new HashMap<>();
        completeData.put("status", "COMPLETED");
        completeData.put("message", "任务完成");
        completeData.put("result", result);
        completeData.put("timestamp", System.currentTimeMillis());

        sendEvent(connectionId, "complete", completeData);
        log.info("完成事件推送，连接ID: {}", connectionId);
    }

    public void sendError(String connectionId, String errorMessage) {
        sendError(connectionId, "ERROR", errorMessage);
    }

    public void sendError(String connectionId, String errorCode, String errorMessage) {
        Map<String, Object> errorData = new HashMap<>();
        errorData.put("status", "FAILED");
        errorData.put("errorCode", errorCode);
        errorData.put("message", errorMessage);
        errorData.put("timestamp", System.currentTimeMillis());

        sendEvent(connectionId, "error", errorData);
        log.error("错误事件推送，连接ID: {}, 错误码: {}, 错误信息: {}", connectionId, errorCode, errorMessage);
    }

    public void sendHeartbeat(String connectionId) {
        SseEmitter emitter = connections.get(connectionId);
        if (emitter != null) {
            try {
                emitter.send(SseEmitter.event()
                        .name("heartbeat")
                        .data("{\"timestamp\":" + System.currentTimeMillis() + "}"));
                
                ConnectionInfo info = connectionInfos.get(connectionId);
                if (info != null) {
                    info.setLastActivityTime(System.currentTimeMillis());
                }
                
            } catch (IOException e) {
                log.warn("发送手动心跳失败，连接ID: {}", connectionId);
                handleDisconnection(connectionId, "HEARTBEAT_FAILED");
            }
        }
    }

    public void removeConnection(String connectionId) {
        SseEmitter emitter = connections.remove(connectionId);
        ConnectionInfo info = connectionInfos.remove(connectionId);
        
        if (emitter != null) {
            try {
                emitter.complete();
                log.info("连接已移除，连接ID: {}", connectionId);
            } catch (Exception e) {
                log.warn("关闭连接时出错，连接ID: {}", connectionId, e);
            }
        }
        
        if (info != null) {
            notifyEvent(connectionId, ConnectionEvent.DISCONNECTED, info);
        }
    }

    public boolean hasConnection(String connectionId) {
        return connections.containsKey(connectionId);
    }

    public int getConnectionCount() {
        return connections.size();
    }

    public Map<String, SseEmitter> getAllConnections() {
        synchronized (connections) {
            return new HashMap<>(connections);
        }
    }

    public void broadcast(String eventName, Object data) {
        log.info("广播事件，事件名: {}, 接收者数量: {}", eventName, connections.size());

        for (String connectionId : connections.keySet()) {
            sendEvent(connectionId, eventName, data);
        }
    }

    public void cleanupExpiredConnections() {
        log.debug("开始清理过期连接，当前连接数: {}", connections.size());
        List<String> toRemove = new ArrayList<>();
        
        for (Map.Entry<String, SseEmitter> entry : connections.entrySet()) {
            try {
                entry.getValue().send(SseEmitter.event().comment("ping"));
            } catch (IOException e) {
                log.warn("发现失效连接，连接ID: {}", entry.getKey());
                toRemove.add(entry.getKey());
            }
        }
        
        for (String connId : toRemove) {
            handleDisconnection(connId, "EXPIRED");
        }
        
        log.debug("清理完成，剩余连接数: {}", connections.size());
    }

    public void addConnectionEventListener(ConnectionEventListener listener) {
        eventListeners.add(listener);
        log.debug("添加连接事件监听器，当前监听器数量: {}", eventListeners.size());
    }

    public void removeConnectionEventListener(ConnectionEventListener listener) {
        eventListeners.remove(listener);
        log.debug("移除连接事件监听器，当前监听器数量: {}", eventListeners.size());
    }

    public ConnectionInfo getConnectionInfo(String connectionId) {
        return connectionInfos.get(connectionId);
    }

    public Map<String, ConnectionInfo> getAllConnectionInfos() {
        synchronized (connectionInfos) {
            return new HashMap<>(connectionInfos);
        }
    }

    private void handleDisconnection(String connectionId, String reason) {
        log.info("处理连接断开，连接ID: {}, 原因: {}", connectionId, reason);
        
        SseEmitter emitter = connections.remove(connectionId);
        ConnectionInfo info = connectionInfos.remove(connectionId);
        
        if (emitter != null) {
            try {
                emitter.complete();
            } catch (Exception e) {
                log.warn("关闭连接时出错，连接ID: {}", connectionId, e);
            }
        }
        
        Map<String, Object> eventData = new HashMap<>();
        eventData.put("reason", reason);
        if (info != null) {
            eventData.put("info", info);
        }
        notifyEvent(connectionId, ConnectionEvent.DISCONNECTED, eventData);
    }

    private void notifyEvent(String connectionId, ConnectionEvent event, Object data) {
        for (ConnectionEventListener listener : eventListeners) {
            try {
                listener.onEvent(connectionId, event, data);
            } catch (Exception e) {
                log.error("事件监听器执行异常，连接ID: {}, 事件: {}", connectionId, event, e);
            }
        }
    }

    private String convertToJson(Object data) {
        if (data == null) {
            return "{}";
        }
        if (data instanceof String) {
            return (String) data;
        }
        try {
            return objectMapper.writeValueAsString(data);
        } catch (Exception e) {
            log.error("对象转JSON失败", e);
            return "{\"error\":\"JSON serialization failed\"}";
        }
    }

    private Map<String, Object> buildMessage(String status, String message, Object data) {
        Map<String, Object> result = new HashMap<>();
        result.put("status", status);
        result.put("message", message);
        result.put("data", data != null ? data : "");
        result.put("timestamp", System.currentTimeMillis());
        return result;
    }

    public void shutdown() {
        running.set(false);
        heartbeatScheduler.shutdown();
        cleanupScheduler.shutdown();

        for (String connectionId : connections.keySet()) {
            handleDisconnection(connectionId, "SHUTDOWN");
        }

        log.info("SSE推送服务已关闭");
    }

    public enum ConnectionEvent {
        CONNECTED,
        DISCONNECTED,
        TIMEOUT,
        ERROR,
        MESSAGE_SENT
    }

    public static class ConnectionInfo {
        private final String connectionId;
        private final long createTime;
        private volatile long lastActivityTime;
        private volatile long lastMessageTime;
        private volatile int messageCount;
        private volatile String status;

        public ConnectionInfo(String connectionId) {
            this.connectionId = connectionId;
            this.createTime = System.currentTimeMillis();
            this.lastActivityTime = this.createTime;
            this.lastMessageTime = 0;
            this.messageCount = 0;
            this.status = "ACTIVE";
        }

        public String getConnectionId() { return connectionId; }
        public long getCreateTime() { return createTime; }
        public long getLastActivityTime() { return lastActivityTime; }
        public void setLastActivityTime(long lastActivityTime) { this.lastActivityTime = lastActivityTime; }
        public long getLastMessageTime() { return lastMessageTime; }
        public void setLastMessageTime(long lastMessageTime) { this.lastMessageTime = lastMessageTime; }
        public int getMessageCount() { return messageCount; }
        public void incrementMessageCount() { this.messageCount++; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public long getDuration() { return System.currentTimeMillis() - createTime; }
    }

    @FunctionalInterface
    public interface ConnectionEventListener {
        void onEvent(String connectionId, ConnectionEvent event, Object data);
    }
}
