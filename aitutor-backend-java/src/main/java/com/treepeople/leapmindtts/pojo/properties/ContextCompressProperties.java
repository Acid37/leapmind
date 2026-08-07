package com.treepeople.leapmindtts.pojo.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "context.compress")
public class ContextCompressProperties {
    private int tokenThreshold = 4000;
    private int maxCompressedTokens = 1000;
}
