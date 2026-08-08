package com.treepeople.leapmindtts.service.virtualteacher;

import com.treepeople.leapmindtts.config.VirtualTeacherProperties;
import com.treepeople.leapmindtts.pojo.dto.VirtualTeacherTtsRequest;
import com.treepeople.leapmindtts.service.lesson.TextToSpeechService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VirtualTeacherTtsServiceTest {
    @Mock
    private TextToSpeechService textToSpeechService;
    @Mock
    private VirtualTeacherTtsCache cache;
    @Mock
    private AudioStorageService storage;

    private VirtualTeacherTtsService service;
    private VirtualTeacherProperties properties;
    private VirtualTeacherTtsRequest request;

    @BeforeEach
    void setUp() {
        properties = new VirtualTeacherProperties();
        service = new VirtualTeacherTtsService(textToSpeechService, cache, storage, properties);
        request = new VirtualTeacherTtsRequest();
        request.setText("同学们好");
        request.setVoiceType("zhixiaoxia");
        request.setSpeed(1.0);
    }

    @Test
    void returnsCachedAudioWithoutCallingProvider() {
        byte[] audio = {1, 2, 3};
        when(cache.get(anyString())).thenReturn(Optional.of("cached.wav"));
        when(storage.load("cached.wav")).thenReturn(Optional.of(audio));
        when(storage.createReadUrl("cached.wav")).thenReturn("/audio/cached.wav");

        VirtualTeacherTtsService.SynthesisResult result = service.synthesize(request);

        assertTrue(result.response().isCacheHit());
        assertArrayEquals(audio, result.audio());
        verify(textToSpeechService, never()).synthesizeSpeech(anyString(), anyString(), eq(1.0));
    }

    @Test
    void synthesizesStoresAndCachesOnMiss() {
        byte[] audio = {4, 5, 6};
        when(cache.get(anyString())).thenReturn(Optional.empty());
        when(textToSpeechService.synthesizeSpeech("同学们好", "zhixiaoxia", 1.0))
                .thenReturn(Mono.just(audio));
        when(storage.createReadUrl(anyString())).thenReturn("/audio/new.wav");

        VirtualTeacherTtsService.SynthesisResult result = service.synthesize(request);

        assertFalse(result.response().isCacheHit());
        assertArrayEquals(audio, result.audio());
        verify(storage).store(anyString(), eq(audio), eq("audio/wav"));
        verify(cache).put(anyString(), anyString());
    }
}
