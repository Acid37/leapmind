package com.treepeople.leapmindtts.service.virtualteacher;

import com.treepeople.leapmindtts.mapper.VirtualTeacherTtsAuditLogMapper;
import com.treepeople.leapmindtts.pojo.dto.VirtualTeacherTtsRequest;
import com.treepeople.leapmindtts.pojo.entity.VirtualTeacherTtsAuditLog;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class VirtualTeacherAuditService {
    private final VirtualTeacherTtsAuditLogMapper auditLogMapper;

    public void recordTts(
            Long userId,
            VirtualTeacherTtsRequest request,
            VirtualTeacherTtsService.SynthesisResult result,
            long latencyMs) {
        VirtualTeacherTtsAuditLog logEntry = baseLog(userId, request, latencyMs);
        logEntry.setStatus("SUCCESS");
        logEntry.setCacheHit(result.response().isCacheHit());
        logEntry.setAudioSize(result.response().getAudioSize());
        insertSafely(logEntry);
    }

    public void recordTtsFailure(
            Long userId,
            VirtualTeacherTtsRequest request,
            Throwable error,
            long latencyMs) {
        VirtualTeacherTtsAuditLog logEntry = baseLog(userId, request, latencyMs);
        logEntry.setStatus("FAILED");
        logEntry.setCacheHit(false);
        logEntry.setAudioSize(0L);
        logEntry.setErrorMessage(truncate(error.getMessage(), 500));
        insertSafely(logEntry);
    }

    private VirtualTeacherTtsAuditLog baseLog(Long userId, VirtualTeacherTtsRequest request, long latencyMs) {
        VirtualTeacherTtsAuditLog logEntry = new VirtualTeacherTtsAuditLog();
        logEntry.setUserId(userId);
        logEntry.setCourseId(truncate(request.getCourseId(), 64));
        logEntry.setVoiceType(truncate(request.getVoiceType(), 100));
        logEntry.setTextLength(request.getText() == null ? 0 : request.getText().trim().length());
        logEntry.setLatencyMs(latencyMs);
        return logEntry;
    }

    private void insertSafely(VirtualTeacherTtsAuditLog logEntry) {
        try {
            auditLogMapper.insert(logEntry);
        } catch (RuntimeException error) {
            log.warn("写入虚拟教师 TTS 审计日志失败: {}", error.getMessage());
        }
    }

    private String truncate(String value, int maxLength) {
        if (value == null) return null;
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
