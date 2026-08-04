package com.treepeople.leapmindtts.controller.lesson;

import com.treepeople.leapmindtts.pojo.dto.VoiceChatRequest;
import com.treepeople.leapmindtts.pojo.dto.VoiceChatResponse;
import com.treepeople.leapmindtts.pojo.dto.VoiceSynthesisRequest;
import com.treepeople.leapmindtts.service.lesson.VoiceChatService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @ Package：com.treepeople.leapmindtts.controller
 * @ Project：leapmind-tts - 语音对话
 * @ Description: 语音对话控制器
 * @ Date：2025/8/8
 */

@Slf4j
@RestController
@RequestMapping("/api/voice-chat")
@RequiredArgsConstructor
public class VoiceChatController {

    private final VoiceChatService voiceChatService;

    //use:语音问答接口 111

    /**
     * 处理语音对话请求
     * 接收用户问题，调用AI服务获取回答
     * 注意：从响应式改为同步调用，解决Spring MVC与WebFlux混用导致的403问题
     */
    @PostMapping("/ask")
    public ResponseEntity<VoiceChatResponse> handleVoiceChat(
            @RequestBody @Valid VoiceChatRequest request) {
        log.info("接收到语音对话请求，会话ID: {}, 问题: {}",
                request.getCourseId(), request.getQuestion());

        try {
            // 使用.block()将响应式调用转换为同步调用
            String answer = voiceChatService.processVoiceChat(request.getCourseId(), request.getQuestion())
                    .block();

            VoiceChatResponse response = new VoiceChatResponse(
                    answer,
                    request.getCourseId(),
                    "SUCCESS"
            );
            log.info("语音对话处理完成，会话ID: {}, AI回答: {}",
                    request.getCourseId(), answer);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("处理语音对话失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new VoiceChatResponse(
                            "处理问题时出现错误：" + e.getMessage(),
                            request.getCourseId(),
                            "ERROR"));
        }
    }


    //use:语音合成接口

    /**
     * 合成语音
     * 将文本转换为语音音频
     * 注意：从响应式改为同步调用，解决Spring MVC与WebFlux混用导致的403问题
     */
    @PostMapping("/synthesize")
    public ResponseEntity<byte[]> synthesizeVoice(
            @RequestBody @Valid VoiceSynthesisRequest request) {
        log.info("接收到语音合成请求，会话ID: {}, 文本长度: {}",
                request.getCourseId(), request.getText().length());

        try {
            // 使用.block()将响应式调用转换为同步调用
            byte[] audioData = voiceChatService.synthesizeVoiceAudio(request.getText())
                    .block();

            log.info("语音合成完成，会话ID: {}, 音频大小: {} bytes",
                    request.getCourseId(), audioData.length);
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType("audio/wav"))
                    .body(audioData);

        } catch (Exception error) {
            log.error("语音合成失败，会话ID: {}", request.getCourseId(), error);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new byte[0]);
        }
    }
}
