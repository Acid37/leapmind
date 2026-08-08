package com.treepeople.leapmindtts.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Data
@Component
@ConfigurationProperties(prefix = "conversation.session")
public class ConversationProperties {

    private Duration timeout = Duration.ofMinutes(30);

    private int messageLimit = 20;
}