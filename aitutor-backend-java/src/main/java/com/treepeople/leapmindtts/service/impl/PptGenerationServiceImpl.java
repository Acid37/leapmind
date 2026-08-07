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
        return executeAsync(prepId, null, null);
    }
    public PipelineResult executeAsync(Long prepId, String connectionId) {
        return executeAsync(prepId, connectionId, null);
    }

    /** PPT 生成管道异步启动：PPTX导出 + TTS，SSE 推送 0-100% 进度。 */
    public PipelineResult executeAsync(Long prepId, String connectionId, String userJwt) {
        if (connectionId == null || connectionId.isEmpty()) {
            connectionId = "pipe-" + UUID.randomUUID().toString().substring(0, 8);
        }
        final String connId = connectionId;
        final String jwt = userJwt;

        PipelineResult result = new PipelineResult();
        result.setConnectionId(connId);
        result.setStartTime(System.currentTimeMillis());

        CompletableFuture.runAsync(() -> {
            try {
                PipelineResult r = execute(prepId, connId, jwt);
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

    /** PPT 生成管道同步执行：PPTX导出(10%)→(20-50%)→TTS旁白(50-95%)→(100%)。 */
    public PipelineResult execute(Long prepId, String connectionId, String userJwt) {
        PipelineResult result = new PipelineResult();
        result.setConnectionId(connectionId);
        result.setStartTime(System.currentTimeMillis());

        try {
            sseService.sendProgress(connectionId, 0, "STARTED", "开始处理备课 " + prepId);

            TeachingContent content = mapper.selectByPrepId(prepId);
            if (content == null) throw new IllegalArgumentException("备课不存在: " + prepId);

            PptStructureDTO structure = PptStructureDTO.parse(om, content.getPptStructure());
            int totalSlides = structure.getSlides() != null ? structure.getSlides().size() : 0;
            result.setTotalSlides(totalSlides);

            // 阶段 1：导出 PPTX → 回填 ppt_download_url（进度 10%→50%）
            sseService.sendProgress(connectionId, 10, "PROCESSING", "正在生成PPTX...");
            String pptUrl = pptxService.exportFromStructure(structure, content.getTemplateId(), content.getTitle());
            result.setPptDownloadUrl(pptUrl);
            try {
             content.setPptDownloadUrl(pptUrl);
                mapper.updateById(content);
            } catch (Exception dbEx) {
                log.warn("Pipeline 回写 ppt_download_url 失败（不影响最终结果）, prepId={}, err={}", prepId, dbEx.getMessage());
            }
            sseService.sendProgress(connectionId, 50, "PROCESSING", "PPTX生成成功");

            // 阶段 2：一键 TTS（进度 50→95%）。先统计 tasks 总量给 SSE。
            int narrationTasks = countNarrationTasks(structure);
            sseService.sendProgress(connectionId, 50, "PROCESSING", "开始生成 " + narrationTasks + " 个页面旁白");

            int ok = ttsService.generateAndBackfill(structure, prepId, userJwt, p -> {
                int pct = 50 + (int) (p.getCurrentIndex() * 45.0 / Math.max(1, narrationTasks));
                sseService.sendProgress(connectionId, Math.min(95, pct), "PROCESSING",
                        "旁白 " + p.getCurrentIndex() + "/" + narrationTasks + ": " + p.getCurrentTitle());
                if ("COMPLETED".equals(p.getStatus()) && p.getAudioUrl() != null) {
                    if (result.getAudioUrls() == null) result.setAudioUrls(new HashMap<>());
                    result.getAudioUrls().put(p.getCurrentIndex(), p.getAudioUrl());
                }
            });

            result.setSuccessCount(ok);
            result.setFailCount(narrationTasks - ok);

            sseService.sendProgress(connectionId, 100, "COMPLETED",
                    "完成! 成功: " + ok + ", 失败: " + (narrationTasks - ok));
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

    private static int countNarrationTasks(PptStructureDTO s) {
        if (s == null || s.getSlides() == null) return 0;
        int n = 0;
        for (PptStructureDTO.SlideDTO sd : s.getSlides()) {
            String nt = sd.getNotes();
            if (nt != null && !nt.trim().isEmpty() && !nt.startsWith("[AUDIO_URL:")) n++;
        }
        return n;
    }

    public enum PipelineStep {
        INITIALIZE("初始化", 0), PARSE_PPT("解析 PPT 结构", 5),
        GENERATE_PPTX("生成 PPTX 文件", 20), UPLOAD_PPTX("上传 PPTX 到 MinIO", 40),
        GENERATE_NARRATIONS("生成旁白音频", 50), UPLOAD_AUDIOS("上传音频到 MinIO", 80),
        UPDATE_DATABASE("更新数据库", 90), COMPLETE("完成", 100);
        private final String name;
        private final int progress;
        PipelineStep(String name, int progress) { this.name = name; this.progress = progress; }
        public String getName() { return name; }
        public int getProgress() { return progress; }
    }

    @lombok.Data
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
        public long getDuration() { return endTime - startTime; }
    }
}
 