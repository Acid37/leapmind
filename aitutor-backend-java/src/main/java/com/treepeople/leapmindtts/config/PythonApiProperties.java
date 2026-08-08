
 package com.treepeople.leapmindtts.config;
 
 import lombok.Data;
 import org.springframework.boot.context.properties.ConfigurationProperties;
 import org.springframework.stereotype.Component;
 
 @Data
 @Component("optimizePythonApiProperties")
 @ConfigurationProperties(prefix = "python.api")
 public class PythonApiProperties {
     private String compressContextUri;
 }
