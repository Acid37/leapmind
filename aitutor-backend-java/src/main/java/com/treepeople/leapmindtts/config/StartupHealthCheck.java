package com.treepeople.leapmindtts.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * 启动时健康检查
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StartupHealthCheck implements CommandLineRunner {

    @Value("${asr.api.key}")
    private String asrApiKey;

    @Value("${tts.api.key}")
    private String ttsApiKey;

    @Value("${ai.api.key}")
    private String aiApiKey;

    @Override
    public void run(String... args) {
        log.info("=== 启动健康检查 ===");/**/
        
        // 检查API Key配置
        checkApiKey("ASR", asrApiKey);
        checkApiKey("TTS", ttsApiKey);
        checkApiKey("AI", aiApiKey);
        
        /*log.info("=== 健康检查完成 ===");
        log.info("如果遇到400错误，请检查:");
        log.info("1. API Key是否正确且有效");
        log.info("2. 音频数据格式是否正确");
        log.info("3. 网络连接是否正常");
        log.info("4. 阿里云服务是否正常");*/
    }

    private void checkApiKey(String serviceName, String apiKey) {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            log.warn("{} API Key 未配置", serviceName);
        } else {
            log.info("{} API Key 已配置: {}...", serviceName, 
                    apiKey.substring(0, Math.min(8, apiKey.length())));
        }
    }
}