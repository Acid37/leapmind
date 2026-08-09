
 package com.treepeople.leapmindtts.config;
 
 import lombok.Data;
 import org.springframework.boot.context.properties.ConfigurationProperties;
 import org.springframework.stereotype.Component;
 
 @Data
 @Component("optimizeContextCompressProperties")
 @ConfigurationProperties(prefix = "context.compress")
 public class ContextCompressProperties {
     private int tokenThreshold = 4000; // Default threshold
     private int maxCompressedTokens = 1000; // Default max
 }
