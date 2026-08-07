
 package com.treepeople.leapmindtts.util;
 
 import org.springframework.util.DigestUtils;
 
 import java.nio.charset.StandardCharsets;
 
 public class CacheKeyBuilder {
     public static String questionHash(String question) {
         // 去掉首尾多余空白，把内部连续空格缩减为单空格，转为全小写，再求 MD5 保证对齐标准
         String normalized = question.trim().toLowerCase().replaceAll("\\s+", " ");
         return DigestUtils.md5DigestAsHex(normalized.getBytes(StandardCharsets.UTF_8));
     }
     public static String dedupKey(String userId, String question) {
         return userId + ":" + questionHash(question);
     }
     public static final String QUESTION_CACHE_PREFIX = "qa:cache:";
 }
 