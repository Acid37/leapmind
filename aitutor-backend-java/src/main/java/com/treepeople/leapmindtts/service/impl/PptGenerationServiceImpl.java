package com.treepeople.leapmindtts.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.treepeople.leapmindtts.mapper.TeachingContentMapper;
import com.treepeople.leapmindtts.pojo.dto.PptStructureDTO;
import com.treepeople.leapmindtts.pojo.entity.TeachingContent;
import com.treepeople.leapmindtts.service.PptxExportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
@RequiredArgsConstructor
public class PptGenerationServiceImpl {

    private final PptxExportService pptxService;
    private final TtsBatchServiceImpl ttsService;
    private final SsePushServiceImpl sseService;
    private final ObjectMapper om;
    private final TeachingContentMapper mapper;

    public PipelineResult executeAsync(Long prepId) {
        return executeAsync(prepId, null);
    }

    public PipelineResult executeAsync(Long prepId, String connectionId) {
        if (connectionId == null || connectionId.isEmpty()) {
            connectionId = "pipe-" + UUID.randomUUID().toString().substring(0, 8);
        }
        final String connId = connectionId;

        PipelineResult result = new PipelineResult();
        result.setConnectionId(connId);
        result.setStartTime(System.currentTimeMillis());

        CompletableFuture.runAsync(() -> {
            try {
                PipelineResult r = execute(prepId, connId);
                result.setSuccess(r.isSuccess());
                result.setMessage(r.getMessage());
                result.setPptDownloadUrl(r.getPptDownloadUrl());
                result.setAudioUrls(r.getAudioUrls());
                result.setTotalSlides(r.getTotalSlides());
                result.setSuccessCount(r.getSuccessCount());
                result.setFailCount(r.getFailCount());

                if (r.isSuccess()) {
                    Map<String, Object> data = new HashMap<>();
                    data.put("status", "COMPLETED");
                    data.put("message", r.getMessage());
                    data.put("pptDownloadUrl", r.getPptDownloadUrl());
                    data.put("totalSlides", r.getTotalSlides());
                    sseService.sendComplete(connId, data);
                }
            } catch (Exception e) {
                log.error("管道异常, prepId={}", prepId, e);
                result.setSuccess(false);
                result.setMessage(e.getMessage());
                sseService.sendError(connId, "PIPELINE_ERROR", e.getMessage());
            } finally {
                result.setEndTime(System.currentTimeMillis());
            }
        });

        return result;
    }

    public PipelineResult execute(Long prepId, String connectionId) {
        PipelineResult result = new PipelineResult();
        result.setConnectionId(connectionId);
        result.setStartTime(System.currentTimeMillis());

        try {
            sseService.sendProgress(connectionId, 0, "STARTED", "开始处理备课 " + prepId);

            TeachingContent content = mapper.selectByPrepId(prepId);
            if (content == null) throw new IllegalArgumentException("备课不存在: " + prepId);

            // 约定：teaching_contents.ppt_structure 列必须已是 Python 端 convert_keys_camel 处理好的格式：
            //   顶层 {"pptId":N,"slides":[...]} 对象，所有键 camelCase（递归到嵌套层）
            // Java 端不做任何 snake→camel 转换；仅当解析失败且 JSON 以 [ 开头时
            // 进行"纯数组 → 对象"内存包装兜底（不改 DB / 不改 Python）
            String json = content.getPptStructure();
            if (json == null || json.isEmpty()) throw new IllegalArgumentException("PPT结构为空");

            PptStructureDTO structure = PptStructureDTO.parse(om, json);
            int totalSlides = structure.getSlides() != null ? structure.getSlides().size() : 0;
            result.setTotalSlides(totalSlides);

            sseService.sendProgress(connectionId, 10, "PROCESSING", "正在生成PPTX...");
            String pptUrl = pptxService.exportFromStructure(structure, content.getTemplateId(), content.getTitle());
            result.setPptDownloadUrl(pptUrl);

            // 回写下载 URL 到已有列 ppt_download_url（不新增列、不改库）
            try {
                content.setPptDownloadUrl(pptUrl);
                mapper.updateById(content);
                log.info("Pipeline 已更新 teaching_contents.ppt_download_url, prepId={}, url={}", prepId, pptUrl);
            } catch (Exception dbEx) {
                log.warn("Pipeline 回写 ppt_download_url 失败（不影响最终结果）, prepId={}, err={}", prepId, dbEx.getMessage());
            }

            sseService.sendProgress(connectionId, 40, "PROCESSING", "PPTX生成成功");

            List<TtsBatchServiceImpl.NarrationTask> tasks = new ArrayList<>();
            if (structure.getSlides() != null) {
                for (int i = 0; i < structure.getSlides().size(); i++) {
                    PptStructureDTO.SlideDTO slide = structure.getSlides().get(i);
                    String notes = slide.getNotes();
                    if (notes != null && !notes.isEmpty() && !notes.startsWith("[AUDIO_URL:")) {
                        tasks.add(new TtsBatchServiceImpl.NarrationTask(i, notes, slide.getTitle()));
                    }
                }
            }

            sseService.sendProgress(connectionId, 50, "PROCESSING", "开始生成 " + tasks.size() + " 个页面旁白");

            Map<Integer, String> audioUrls = new HashMap<>();
            if (!tasks.isEmpty()) {
                audioUrls = ttsService.generateAndUploadNarrations(tasks, prepId, info -> {
                    int pct = 50 + (int) (info.getCurrentIndex() * 50.0 / tasks.size());
                    sseService.sendProgress(connectionId, pct, "PROCESSING",
                            "旁白 " + info.getCurrentIndex() + "/" + tasks.size() + ": " + info.getCurrentTitle());
                });
            }

            result.setAudioUrls(audioUrls);
            result.setSuccessCount(audioUrls.size());
            result.setFailCount(tasks.size() - audioUrls.size());

            if (!audioUrls.isEmpty()) {
                for (Map.Entry<Integer, String> e : audioUrls.entrySet()) {
                    int idx = e.getKey();
                    String url = e.getValue();
                    if (idx >= 0 && idx < structure.getSlides().size()) {
                        PptStructureDTO.SlideDTO slide = structure.getSlides().get(idx);
                        String n = slide.getNotes();
                        if (n != null && !n.startsWith("[AUDIO_URL:")) {
                            slide.setNotes("[AUDIO_URL:" + url + "]\n" + n);
                        } else {
                            slide.setNotes("[AUDIO_URL:" + url + "]");
                        }
                    }
                }
                content.setPptStructure(om.writeValueAsString(structure));
                mapper.updateById(content);
            }

            sseService.sendProgress(connectionId, 100, "COMPLETED",
                    "完成! 成功: " + audioUrls.size() + ", 失败: " + (tasks.size() - audioUrls.size()));

            result.setSuccess(true);
            result.setMessage("PPT生成管道执行成功");

        } catch (Exception e) {
            log.error("管道执行失败", e);
            result.setSuccess(false);
            result.setMessage(e.getMessage());
            sseService.sendError(connectionId, "PIPELINE_FAILED", e.getMessage());
        } finally {
            result.setEndTime(System.currentTimeMillis());
        }

        return result;
    }

    public enum PipelineStep {
        INITIALIZE("初始化", 0),
        PARSE_PPT("解析 PPT 结构", 5),
        GENERATE_PPTX("生成 PPTX 文件", 20),
        UPLOAD_PPTX("上传 PPTX 到 MinIO", 40),
        GENERATE_NARRATIONS("生成旁白音频", 50),
        UPLOAD_AUDIOS("上传音频到 MinIO", 80),
        UPDATE_DATABASE("更新数据库", 90),
        COMPLETE("完成", 100);

        private final String name;
        private final int progress;

        PipelineStep(String name, int progress) {
            this.name = name;
            this.progress = progress;
        }

        public String getName() { return name; }
        public int getProgress() { return progress; }
    }

    public static class PipelineResult {
        private boolean success;
        private String message;
        private String connectionId;
        private String taskId;
        private String pptDownloadUrl;
        private Map<Integer, String> audioUrls;
        private int totalSlides;
        private int successCount;
        private int failCount;
        private long startTime;
        private long endTime;

        public PipelineResult() {}

        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
        public String getConnectionId() { return connectionId; }
        public void setConnectionId(String connectionId) { this.connectionId = connectionId; }
        public String getTaskId() { return taskId; }
        public void setTaskId(String taskId) { this.taskId = taskId; }
        public String getPptDownloadUrl() { return pptDownloadUrl; }
        public void setPptDownloadUrl(String pptDownloadUrl) { this.pptDownloadUrl = pptDownloadUrl; }
        public Map<Integer, String> getAudioUrls() { return audioUrls; }
        public void setAudioUrls(Map<Integer, String> audioUrls) { this.audioUrls = audioUrls; }
        public int getTotalSlides() { return totalSlides; }
        public void setTotalSlides(int totalSlides) { this.totalSlides = totalSlides; }
        public int getSuccessCount() { return successCount; }
        public void setSuccessCount(int successCount) { this.successCount = successCount; }
        public int getFailCount() { return failCount; }
        public void setFailCount(int failCount) { this.failCount = failCount; }
        public long getStartTime() { return startTime; }
        public void setStartTime(long startTime) { this.startTime = startTime; }
        public long getEndTime() { return endTime; }
        public void setEndTime(long endTime) { this.endTime = endTime; }
        public long getDuration() { return endTime - startTime; }
    }
}
