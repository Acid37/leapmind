package com.treepeople.leapmindtts.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class BaiduSpeechConfig {

    @Value("${baidu.speech.app.id}")
    private String baiduAppId;

    @Value("${baidu.speech.api.key}")
    private String baiduApiKey;

    @Value("${baidu.speech.secret.key}")
    private String baiduSecretKey;

    @Value("${baidu.speech.format:pcm}")
    private String audioFormat;

    @Value("${baidu.speech.rate:16000}")
    private int sampleRate;

    @Value("${baidu.speech.dev.pid:1537}")
    private int devPid;

    public String getBaiduAppId() {
        return baiduAppId;
    }

    public String getBaiduApiKey() {
        return baiduApiKey;
    }

    public String getBaiduSecretKey() {
        return baiduSecretKey;
    }

    public String getAudioFormat() {
        return audioFormat;
    }

    public int getSampleRate() {
        return sampleRate;
    }

    public int getDevPid() {
        return devPid;
    }
} 