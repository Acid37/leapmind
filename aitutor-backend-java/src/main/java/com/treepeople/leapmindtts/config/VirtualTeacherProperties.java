package com.treepeople.leapmindtts.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Data
@Component
@ConfigurationProperties(prefix = "virtual-teacher")
public class VirtualTeacherProperties {
    private Duration cacheTtl = Duration.ofHours(24);
    private Duration synthesisTimeout = Duration.ofSeconds(125);
    private Storage storage = new Storage();

    @Data
    public static class Storage {
        private String type = "local";
        private String localDir = "${java.io.tmpdir}/leapmind-tts";
        private String publicBaseUrl = "";
        private String endpoint = "http://localhost:9000";
        private String accessKey = "minioadmin";
        private String secretKey = "minioadmin";
        private String bucket = "leapmind-tts";
    }
}
