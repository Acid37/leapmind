package com.treepeople.leapmindtts.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.treepeople.leapmindtts.mapper.TeachingContentMapper;
import com.treepeople.leapmindtts.pojo.dto.PptStructureDTO;
import com.treepeople.leapmindtts.pojo.entity.TeachingContent;
import com.treepeople.leapmindtts.service.lesson.TextToSpeechService;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Service
@RequiredArgsConstructor
public class TtsBatchServiceImpl {

    private final TextToSpeechService ttsService;
    private final MinioClient minioClient;
    private final SsePushServiceImpl sseService;
    private final ObjectMapper objectMapper;
    private final TeachingContentMapper contentMapper;

    @Value("${minio.bucket-name:leapmind}")
    private String bucketName;

    @Value("${minio.endpoint:http://127.0.0.1:9000}")
    private String endpoint;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy/MM/dd");
    private static final String AUDIO_PREFIX = "tts-narrations/";
    private static final int SINGLE_TIMEOUT_MS = 60000;
    private static final int MAX_RETRY_COUNT = 3;

    private final ExecutorService pool = new ThreadPoolExecutor(
            3, 3, 60L, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(),
            r -> { Thread t = new Thread(r, "tts-worker"); t.setDaemon(true); return t; },
            new ThreadPoolExecutor.CallerRunsPolicy()
    );

    private final Map<String, TaskStatus> taskMap = new ConcurrentHashMap<>();

    public String generateNarrationsAsync(TeachingContent content, String connectionId) {
        log.info("异步生成旁白, prepId={}", content.getId());
        String taskId = "tts-" + UUID.randomUUID().toString().substring(0, 8);

        TaskStatus status = new TaskStatus();
        status.setTaskId(taskId);
        status.setStatus("PENDING");
        status.setStartTime(System.currentTimeMillis());
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

    public String generateNarrationsAsync(Long prepId, String json, String connectionId) {
        TeachingContent c = new TeachingContent();
        c.setId(prepId);
        c.setPptStructure(json);
        return generateNarrationsAsync(c, connectionId);
    }

    public String generateSingleNarration(int pageIndex, String narration, Long prepId) {
        if (narration == null || narration.trim().isEmpty()) return null;
        try {
            byte[] audio = synthesizeWithRetry(narration);
            if (audio == null || audio.length == 0) return null;
            String url = uploadAudio(audio, prepId, pageIndex);
            log.info("旁白生成成功, page={}, url={}", pageIndex, url);
            return url;
        } catch (Exception e) {
            log.error("生成旁白失败, page={}", pageIndex, e);
            return null;
        }
    }

    public Map<Integer, String> generateAndUploadNarrations(List<NarrationTask> tasks, Long prepId,
                                                              java.util.function.Consumer<ProgressInfo> onProgress) {
        log.info("批量生成旁白, count={}", tasks.size());
        Map<Integer, String> result = new ConcurrentHashMap<>();
        AtomicInteger done = new AtomicInteger(0);

        List<CompletableFuture<Void>> futures = tasks.stream()
                .map(task -> CompletableFuture.runAsync(() -> {
                    int idx = done.incrementAndGet();
                    String url = null;
                    String err = null;
                    try {
                        byte[] audio = synthesizeWithRetry(task.getNarration());
                        if (audio != null && audio.length > 0) {
                            url = uploadAudio(audio, prepId, task.getPageIndex());
                            result.put(task.getPageIndex(), url);
                        } else {
                            err = "音频生成失败";
                        }
                    } catch (Exception e) {
                        err = e.getMessage();
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
        log.info("批量生成完成, success={}", result.size());
        return result;
    }

    public TaskStatus getTaskStatus(String taskId) {
        return taskMap.get(taskId);
    }

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

        PptStructureDTO structure = objectMapper.readValue(json, PptStructureDTO.class);
        List<PptStructureDTO.SlideDTO> slides = structure.getSlides();
        if (slides == null || slides.isEmpty()) {
            log.info("无需生成旁白的页面");
            return;
        }

        List<NarrationTask> tasks = new ArrayList<>();
        for (int i = 0; i < slides.size(); i++) {
            String notes = slides.get(i).getNotes();
            if (notes != null && !notes.trim().isEmpty()) {
                tasks.add(new NarrationTask(i, notes, slides.get(i).getTitle()));
            }
        }

        if (tasks.isEmpty()) {
            log.info("没有需要生成旁白的页面");
            return;
        }

        TaskStatus status = taskMap.get(taskId);
        if (status != null) status.setTotalCount(tasks.size());

        sseService.sendProgress(connectionId, 0, "STARTED", "开始生成 " + tasks.size() + " 个页面的旁白");

        Map<Integer, String> audioUrls = new ConcurrentHashMap<>();
        AtomicInteger done = new AtomicInteger(0);
        AtomicInteger failed = new AtomicInteger(0);
        int total = tasks.size();

        List<CompletableFuture<Void>> futures = tasks.stream()
                .map(task -> CompletableFuture.runAsync(() -> {
                    int cur = done.incrementAndGet();
                    try {
                        byte[] audio = synthesizeWithTimeout(() -> synthesizeWithRetry(task.getNarration()), SINGLE_TIMEOUT_MS);
                        if (audio != null && audio.length > 0) {
                            String url = uploadAudio(audio, content.getId(), task.getPageIndex());
                            audioUrls.put(task.getPageIndex(), url);
                        } else {
                            failed.incrementAndGet();
                        }
                    } catch (Exception e) {
                        failed.incrementAndGet();
                        log.error("处理旁白失败, page={}", task.getPageIndex(), e);
                    }

                    TaskStatus ts = taskMap.get(taskId);
                    if (ts != null) {
                        ts.setCompletedCount(cur);
                        ts.setFailedCount(failed.get());
                    }

                    int progress = (int) ((cur * 100.0) / total);
                    sseService.sendProgress(connectionId, progress, "PROCESSING",
                            "已完成 " + cur + "/" + total + " 个页面");
                }, pool))
                .toList();

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        updateNotesWithAudio(structure, audioUrls);

        if (content.getId() != null) {
            updateDatabase(content.getId(), structure);
        }

        sseService.sendProgress(connectionId, 100, "COMPLETED",
                "旁白生成完成, 成功: " + audioUrls.size() + ", 失败: " + failed.get());
    }

    private byte[] synthesizeWithRetry(String text) {
        for (int i = 1; i <= MAX_RETRY_COUNT; i++) {
            try {
                byte[] audio = ttsService.synthesizeSpeech(text).block();
                if (audio != null && audio.length > 0) return audio;
                log.warn("TTS返回空数据, attempt={}", i);
            } catch (Exception e) {
                log.warn("TTS生成失败, attempt={}, err={}", i, e.getMessage());
                if (i < MAX_RETRY_COUNT) {
                    try { Thread.sleep(1000L * i); } catch (InterruptedException ignored) {}
                }
            }
        }
        return null;
    }

    private <T> T synthesizeWithTimeout(Callable<T> callable, long timeoutMs) throws Exception {
        Future<T> future = pool.submit(callable);
        try {
            return future.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            throw new TimeoutException("音频生成超时");
        }
    }

    private String uploadAudio(byte[] audio, Long prepId, int pageIndex) {
        String datePath = LocalDateTime.now().format(DATE_FMT);
        String objName = String.format("%s%s/prep-%d/page-%d-%s.wav",
                AUDIO_PREFIX, datePath, prepId, pageIndex,
                UUID.randomUUID().toString().substring(0, 8));
        try (InputStream is = new ByteArrayInputStream(audio)) {
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objName)
                            .stream(is, -1, 10485760)
                            .contentType("audio/wav")
                            .build()
            );
            String url = String.format("%s/%s/%s", endpoint, bucketName, objName);
            log.info("音频上传成功, url={}", url);
            return url;
        } catch (Exception e) {
            log.error("音频上传失败, objName={}", objName, e);
            throw new RuntimeException("音频上传失败: " + e.getMessage(), e);
        }
    }

    private void updateNotesWithAudio(PptStructureDTO structure, Map<Integer, String> audioUrls) {
        if (structure.getSlides() == null || audioUrls.isEmpty()) return;
        for (Map.Entry<Integer, String> entry : audioUrls.entrySet()) {
            int idx = entry.getKey();
            String url = entry.getValue();
            if (idx >= 0 && idx < structure.getSlides().size()) {
                PptStructureDTO.SlideDTO slide = structure.getSlides().get(idx);
                String notes = slide.getNotes();
                if (notes != null && !notes.startsWith("[AUDIO_URL:")) {
                    slide.setNotes("[AUDIO_URL:" + url + "]\n" + notes);
                } else {
                    slide.setNotes("[AUDIO_URL:" + url + "]");
                }
            }
        }
    }

    private void updateDatabase(Long prepId, PptStructureDTO structure) {
        try {
            TeachingContent c = contentMapper.selectByPrepId(prepId);
            if (c == null) return;
            c.setPptStructure(objectMapper.writeValueAsString(structure));
            contentMapper.updateById(c);
            log.info("PPT结构已更新, prepId={}", prepId);
        } catch (Exception e) {
            log.error("更新数据库失败, prepId={}", prepId, e);
        }
    }

    public static class NarrationTask {
        private int pageIndex;
        private String narration;
        private String slideTitle;

        public NarrationTask() {}

        public NarrationTask(int pageIndex, String narration, String slideTitle) {
            this.pageIndex = pageIndex;
            this.narration = narration;
            this.slideTitle = slideTitle;
        }

        public int getPageIndex() { return pageIndex; }
        public void setPageIndex(int pageIndex) { this.pageIndex = pageIndex; }
        public String getNarration() { return narration; }
        public void setNarration(String narration) { this.narration = narration; }
        public String getSlideTitle() { return slideTitle; }
        public void setSlideTitle(String slideTitle) { this.slideTitle = slideTitle; }
    }

    public static class TaskStatus {
        private String taskId;
        private String status;
        private int totalCount;
        private int completedCount;
        private int failedCount;
        private String errorMessage;
        private long startTime;
        private long endTime;

        public String getTaskId() { return taskId; }
        public void setTaskId(String taskId) { this.taskId = taskId; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public int getTotalCount() { return totalCount; }
        public void setTotalCount(int totalCount) { this.totalCount = totalCount; }
        public int getCompletedCount() { return completedCount; }
        public void setCompletedCount(int completedCount) { this.completedCount = completedCount; }
        public int getFailedCount() { return failedCount; }
        public void setFailedCount(int failedCount) { this.failedCount = failedCount; }
        public String getErrorMessage() { return errorMessage; }
        public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
        public long getStartTime() { return startTime; }
        public void setStartTime(long startTime) { this.startTime = startTime; }
        public long getEndTime() { return endTime; }
        public void setEndTime(long endTime) { this.endTime = endTime; }

        public int getProgress() {
            if (totalCount == 0) return 0;
            return (int) ((completedCount * 100.0) / totalCount);
        }
    }

    public static class ProgressInfo {
        private int currentIndex;
        private int totalCount;
        private String currentTitle;
        private String status;
        private String audioUrl;
        private String errorMessage;

        public ProgressInfo() {}

        public ProgressInfo(int currentIndex, int totalCount, String currentTitle, String status) {
            this.currentIndex = currentIndex;
            this.totalCount = totalCount;
            this.currentTitle = currentTitle;
            this.status = status;
        }

        public int getCurrentIndex() { return currentIndex; }
        public void setCurrentIndex(int currentIndex) { this.currentIndex = currentIndex; }
        public int getTotalCount() { return totalCount; }
        public void setTotalCount(int totalCount) { this.totalCount = totalCount; }
        public String getCurrentTitle() { return currentTitle; }
        public void setCurrentTitle(String currentTitle) { this.currentTitle = currentTitle; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public String getAudioUrl() { return audioUrl; }
        public void setAudioUrl(String audioUrl) { this.audioUrl = audioUrl; }
        public String getErrorMessage() { return errorMessage; }
        public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

        public int getProgress() {
            if (totalCount == 0) return 0;
            return (int) ((currentIndex * 100.0) / totalCount);
        }
    }
}
