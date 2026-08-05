package com.treepeople.leapmindtts.service.virtualteacher;

import com.treepeople.leapmindtts.config.VirtualTeacherProperties;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import com.treepeople.leapmindtts.pojo.dto.VirtualTeacherTtsRequest;
import com.treepeople.leapmindtts.pojo.vo.VirtualTeacherTtsVO;
import com.treepeople.leapmindtts.service.lesson.TextToSpeechService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class VirtualTeacherTtsService {
    public static final String AUDIO_CONTENT_TYPE = "audio/wav";
    private final TextToSpeechService textToSpeechService;
    private final VirtualTeacherTtsCache cache;
    private final AudioStorageService storage;
    private final VirtualTeacherProperties properties;
    private final VirtualTeacherUsageLimiter usageLimiter;
    private final VirtualTeacherAuditService auditService;
    private final ObjectProvider<MeterRegistry> meterRegistryProvider;

    public SynthesisResult synthesize(VirtualTeacherTtsRequest request) {
        return synthesize(null, request);
    }

    public SynthesisResult synthesize(Long userId, VirtualTeacherTtsRequest request) {
        long startedAt = System.nanoTime();
        int textLength = request.getText() == null ? 0 : request.getText().trim().length();
        try {
            if (userId != null) {
                usageLimiter.check(userId, textLength);
            }
            SynthesisResult result = synthesizeInternal(request);
            long latencyMs = Duration.ofNanos(System.nanoTime() - startedAt).toMillis();
            auditService.recordTts(userId, request, result, latencyMs);
            recordMetrics(result, latencyMs, "success");
            return result;
        } catch (RuntimeException error) {
            long latencyMs = Duration.ofNanos(System.nanoTime() - startedAt).toMillis();
            auditService.recordTtsFailure(userId, request, error, latencyMs);
            recordFailureMetric(error);
            throw error;
        }
    }

    private SynthesisResult synthesizeInternal(VirtualTeacherTtsRequest request) {
        String voice = request.getVoiceType() == null || request.getVoiceType().isBlank()
                ? "default"
                : request.getVoiceType().trim();
        double speed = request.getSpeed() == null ? 1.0 : request.getSpeed();
        String hash = sha256(request.getText().trim() + "\n" + voice + "\n" + speed);
        String cacheKey = "tts:audio:" + hash;

        Optional<String> cachedObjectKey = cache.get(cacheKey);
        if (cachedObjectKey.isPresent()) {
            Optional<byte[]> cachedAudio = storage.load(cachedObjectKey.get());
            if (cachedAudio.isPresent()) {
                return buildResult(cachedAudio.get(), cachedObjectKey.get(), cacheKey, true);
            }
        }

        byte[] audio = textToSpeechService
                .synthesizeSpeech(request.getText().trim(), voice, speed)
                .block(properties.getSynthesisTimeout());
        if (audio == null || audio.length == 0) {
            throw new IllegalStateException("TTS 服务返回空音频");
        }

        String objectKey = hash + ".wav";
        storage.store(objectKey, audio, AUDIO_CONTENT_TYPE);
        cache.put(cacheKey, objectKey);
        return buildResult(audio, objectKey, cacheKey, false);
    }

    private void recordMetrics(SynthesisResult result, long latencyMs, String status) {
        MeterRegistry meterRegistry = meterRegistryProvider.getIfAvailable();
        if (meterRegistry == null) return;
        meterRegistry.counter(
                "virtual_teacher_tts_requests_total",
                "status", status,
                "cache", result.response().isCacheHit() ? "hit" : "miss").increment();
        meterRegistry.summary("virtual_teacher_tts_audio_bytes")
                .record(result.response().getAudioSize());
        Timer.builder("virtual_teacher_tts_latency")
                .tag("status", status)
                .register(meterRegistry)
                .record(Duration.ofMillis(latencyMs));
    }

    private void recordFailureMetric(RuntimeException error) {
        MeterRegistry meterRegistry = meterRegistryProvider.getIfAvailable();
        if (meterRegistry == null) return;
        meterRegistry.counter(
                "virtual_teacher_tts_requests_total",
                "status", "failed",
                "error", error.getClass().getSimpleName()).increment();
    }

    public Optional<byte[]> loadAudio(String objectKey) {
        return storage.load(objectKey);
    }

    private SynthesisResult buildResult(
            byte[] audio,
            String objectKey,
            String cacheKey,
            boolean cacheHit) {
        VirtualTeacherTtsVO response = VirtualTeacherTtsVO.builder()
                .audioUrl(storage.createReadUrl(objectKey))
                .contentType(AUDIO_CONTENT_TYPE)
                .audioSize((long) audio.length)
                .cacheHit(cacheHit)
                .cacheKey(cacheKey)
                .build();
        return new SynthesisResult(audio, response);
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("无法计算 TTS 缓存键", e);
        }
    }

    public record SynthesisResult(byte[] audio, VirtualTeacherTtsVO response) {
    }
}
