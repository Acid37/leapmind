package com.treepeople.leapmindtts.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.treepeople.leapmindtts.client.M8TtsClient;
import com.treepeople.leapmindtts.mapper.TeachingContentMapper;
import com.treepeople.leapmindtts.pojo.dto.PptStructureDTO;
import com.treepeople.leapmindtts.pojo.entity.TeachingContent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

@Slf4j
@Service
@RequiredArgsConstructor
public class TtsBatchServiceImpl {

    private final M8TtsClient m8Client;
    private final SsePushServiceImpl sseService;
    private final ObjectMapper objectMapper;
    private final TeachingContentMapper contentMapper;
    
 /** 3 并发 + CallerRunsPolicy（队列积压时由提交线程兜底执行，避免丢任务）。 */
    private final ExecutorService pool = new ThreadPoolExecutor(
            3, 3, 60L, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(),
            r -> { Thread t = new Thread(r, "tts-worker"); t.setDaemon(true); return t; },
            new ThreadPoolExecutor.CallerRunsPolicy()
    );

    private final Map<String, TaskStatus> taskMap = new ConcurrentHashMap<>();
    
    // ================================================================
    //  异步入口（SSE 进度推送 + 前端"语音就绪"通知）
    // ================================================================

    public String generateNarrationsAsync(TeachingContent content, String connectionId, String userJwt) {
        log.info("异步生成旁白, prepId={}", content.getId());
        String taskId = "tts-" + UUID.randomUUID().toString().substring(0, 8);

        TaskStatus status = new TaskStatus();
        status.setTaskId(taskId);
        status.setStatus("PENDING");
        status.setStartTime(System.currentTimeMillis());
        status.setUserJwt(userJwt);
        taskMap.put(taskId, status);

        CompletableFuture.runAsync(() -> {
            try {
                status.setStatus("PROCESSING");
                doGenerate(content, taskId, connectionId);
                status.setStatus("COMPLETED");
                status.setEndTime(System.currentTimeMillis());
                Map<String, Object> result = new HashMap<>();
                result.put("taskId", taskId);
                result.put("message", "语音生成完成");
                sseService.sendComplete(connectionId, result);
            } catch (Exception e) {
                log.error("旁白生成失败", e);
                status.setStatus("FAILED");
                status.setErrorMessage(e.getMessage());
                status.setEndTime(System.currentTimeMillis());
                sseService.sendError(connectionId, "TTS_FAILED", e.getMessage());
            }
        }, pool);

        return taskId;
    }
    
    public String generateNarrationsAsync(Long prepId, String json, String connectionId, String userJwt) {
        TeachingContent c = new TeachingContent();
        c.setId(prepId);
        c.setPptStructure(json);
        return generateNarrationsAsync(c, connectionId, userJwt);
    }
    
 public String generateSingleNarration(int pageIndex, String narration, Long prepId, String userJwt) {
        if (narration == null || narration.trim().isEmpty()) return null;
        try {
            List<String> urls = m8Client.synthesizeChunkedUrls(
                    narration, prepId != null ? String.valueOf(prepId) : null, null, null, userJwt);
            if (urls == null || urls.isEmpty()) return null;
            String url = (urls.size() == 1) ? urls.get(0) : String.join("|||", urls);
            log.info("旁白生成成功, page={}, audioUrl={}", pageIndex, url);
            return url;
        } catch (Exception e) {
            log.error("生成旁白失败, page={}", pageIndex, e);
            return null;
        }
    }
    
    // ================================================================
    //  核心公共流程：3 并发调 M8 → 收 URL
    // ================================================================

    public Map<Integer, String> generateAndUploadNarrations(
            List<NarrationTask> tasks, Long prepId, String userJwt, Consumer<ProgressInfo> onProgress) {
        log.info("批量生成旁白, count={}", tasks.size());
        Map<Integer, String> result = new ConcurrentHashMap<>();
        AtomicInteger done = new AtomicInteger(0);
        String prepIdStr = prepId != null ? String.valueOf(prepId) : null;

        List<CompletableFuture<Void>> futures = tasks.stream()
                .map(task -> CompletableFuture.runAsync(() -> {
                    int idx = done.incrementAndGet();
                    String url = null;
                    String err = null;
                    try {
                        List<String> urls = m8Client.synthesizeChunkedUrls(
                                task.getNarration(), prepIdStr, null, null, userJwt);
                        if (urls != null && !urls.isEmpty()) {
                            url = (urls.size() == 1) ? urls.get(0) : String.join("|||", urls);
                            result.put(task.getPageIndex(), url);
                        } else {
                            err = "M8 返回空 audioUrl 列表";
                        }
                    } catch (Exception e) {
                        err = "M8 调用失败: " + e.getMessage();
                        log.error("处理旁白失败, page={}", task.getPageIndex(), e);
                    }
                    if (onProgress != null) {
                        ProgressInfo p = new ProgressInfo(idx, tasks.size(), task.getSlideTitle(),
                                url != null ? "COMPLETED" : "FAILED");
                        p.setAudioUrl(url);
                        p.setErrorMessage(err);
                        onProgress.accept(p);
                    }
                }, pool))
                .toList();

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        log.info("批量生成完成, success={}/{}", result.size(), tasks.size());
        return result;
    }

   /**
     * 提炼公共"一键 TTS"：收集 narration → 3 并发调 M8 → 回填 [AUDIO_URL:] → 回写 DB。
     * BulkSpeechServiceImpl / PptGenerationServiceImpl 都调这一处，消除重复代码。
     *
     * @return 成功生成音频的页数
     */
    public int generateAndBackfill(PptStructureDTO structure, Long prepId, String userJwt, Consumer<ProgressInfo> onProgress) {
        if (structure == null || structure.getSlides() == null || structure.getSlides().isEmpty()) return 0;

        List<NarrationTask> tasks = new ArrayList<>();
        for (int i = 0; i < structure.getSlides().size(); i++) {
            String notes = structure.getSlides().get(i).getNotes();
            // 幂等：空 notes / 已带 [AUDIO_URL:] 前缀（已生成）都跳过
            if (notes != null && !notes.trim().isEmpty() && !notes.startsWith("[AUDIO_URL:")) {
                tasks.add(new NarrationTask(i, notes, structure.getSlides().get(i).getTitle()));
            }
        }
        if (tasks.isEmpty()) {
            log.info("无新增需要生成旁白的页面 (slides={})", structure.getSlides().size());
            return 0;
        }

        Map<Integer, String> audioUrls = generateAndUploadNarrations(tasks, prepId, userJwt, onProgress);
        applyAudioUrlsToStructure(structure, audioUrls);

        if (prepId != null) {
            try {
                TeachingContent c = contentMapper.selectByPrepId(prepId);
                if (c != null) {
                    c.setPptStructure(objectMapper.writeValueAsString(structure));
                    contentMapper.updateById(c);
                }
            } catch (Exception e) {
                log.error("TTS 回填回写 DB 失败, prepId={}", prepId, e);
            }
        }
        return audioUrls.size();
    }

    /** 将 {pageIndex→audioUrl} 回填为 slide.notes = "[AUDIO_URL:url]\n原文"。 */
    public static void applyAudioUrlsToStructure(PptStructureDTO structure, Map<Integer, String> audioUrls) {
        if (structure.getSlides() == null || audioUrls == null || audioUrls.isEmpty()) return;
        for (Map.Entry<Integer, String> e : audioUrls.entrySet()) {
            int idx = e.getKey();
            String url = e.getValue();
            if (idx >= 0 && idx < structure.getSlides().size() && url != null) {
                PptStructureDTO.SlideDTO slide = structure.getSlides().get(idx);
                String notes = slide.getNotes() == null ? "" : slide.getNotes();
                // 去掉旧前缀，只保留原文
                String raw = notes.startsWith("[AUDIO_URL:")
                        ? notes.substring(notes.indexOf("]") + 1).trim()
                        : notes;
                slide.setNotes("[AUDIO_URL:" + url + "]\n" + raw);
            }
        }
    }

    // ================================================================
    //  任务状态查询 / 取消
    // ================================================================

    public TaskStatus getTaskStatus(String taskId) { return taskMap.get(taskId); }
    
    public boolean cancelTask(String taskId) {
        TaskStatus s = taskMap.get(taskId);
        if (s != null && "PROCESSING".equals(s.getStatus())) {
            s.setStatus("CANCELLED");
            s.setEndTime(System.currentTimeMillis());
            return true;
        }
        return false;
    }
    
    private void doGenerate(TeachingContent content, String taskId, String connectionId) throws Exception {
        String json = content.getPptStructure();
        if (json == null || json.isEmpty()) throw new IllegalArgumentException("PPT结构数据为空");
        PptStructureDTO structure = PptStructureDTO.parse(objectMapper, json);

        TaskStatus status = taskMap.get(taskId);
        String userJwt = (status != null) ? status.getUserJwt() : null;

        int total = (structure.getSlides() == null) ? 0 : structure.getSlides().size();
        sseService.sendProgress(connectionId, 0, "STARTED", "开始生成 " + total + " 个页面的旁白");

        int ok = generateAndBackfill(structure, content.getId(), userJwt, p -> {
            TaskStatus ts = taskMap.get(taskId);
            if (ts != null) {
                ts.setCompletedCount(p.getCurrentIndex());
                ts.setTotalCount(p.getTotalCount());
                if ("FAILED".equals(p.getStatus())) ts.setFailedCount(ts.getFailedCount() + 1);
            }
            sseService.sendProgress(connectionId, p.getProgress(), p.getStatus(),
                    "已完成 " + p.getCurrentIndex() + "/" + p.getTotalCount());
        });

        int failed = (status != null) ? status.getFailedCount() : 0;
        sseService.sendProgress(connectionId, 100, "COMPLETED",
                "旁白生成完成, 成功: " + ok + ", 失败: " + failed);
    }

    // ================================================================
    //  工具类
    // ================================================================

    /** 从请求头 Authorization: Bearer <token> 中取出纯 token。 */
    public static String extractBearer(String authHeader) {
        if (authHeader == null) return null;
        String s = authHeader.trim();
        if (s.isEmpty()) return null;
        if (s.length() > 7 && s.substring(0, 7).equalsIgnoreCase("bearer ")) {
            return s.substring(7).trim();
        }
        return s;
    }

    @lombok.Data
    public static class NarrationTask {
        private int pageIndex;
        private String narration;
        private String slideTitle;
        public NarrationTask() {}
        public NarrationTask(int pageIndex, String narration, String slideTitle) {
            this.pageIndex = pageIndex; this.narration = narration; this.slideTitle = slideTitle;
        }
    }

    @lombok.Data
    public static class TaskStatus {
        private String taskId;
        private String status;
        private int totalCount;
        private int completedCount;
        private int failedCount;
        private String errorMessage;
        private long startTime;
        private long endTime;
        private String userJwt;
        public int getProgress() {
            if (totalCount == 0) return 0;
            return (int) ((completedCount * 100.0) / totalCount);
        }
    }

    @lombok.Data
    public static class ProgressInfo {
        private int currentIndex;
        private int totalCount;
        private String currentTitle;
        private String status;
        private String audioUrl;
        private String errorMessage;
        public ProgressInfo() {}
        public ProgressInfo(int currentIndex, int totalCount, String currentTitle, String status) {
            this.currentIndex = currentIndex; this.totalCount = totalCount;
            this.currentTitle = currentTitle; this.status = status;
        }
        public int getProgress() {
            if (totalCount == 0) return 0;
            return (int) ((currentIndex * 100.0) / totalCount);
        }
    }
}
