package com.treepeople.leapmindtts.util;

import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 全局片段索引策略
 * 用于生成和解析音频片段的全局唯一索引
 */
@Slf4j
public class SegmentIndexingStrategy {
    
    /**
     * 生成全局片段索引
     * 格式：页码 * 10000 + 内容点索引(两位) * 100 + 句子索引(两位)
     * 
     * 说明：
     * - 将“内容点索引”的位宽从一位(0..9)放宽为两位(0..99)
     * - 页码仍保持 0..999，句子索引保持 0..99
     * 
     * @param pageNumber PPT页码
     * @param contentPointIndex 内容点索引（0..99）
     * @param sentenceIndex 句子索引（0..99）
     * @return 全局片段索引
     */
    public static int generateGlobalSegmentIndex(int pageNumber, int contentPointIndex, int sentenceIndex) {
        if (pageNumber < 0 || pageNumber > 999) {
            throw new IllegalArgumentException("页码必须在0-999之间，当前值: " + pageNumber);
        }
        if (contentPointIndex < 0 || contentPointIndex > 99) {
            throw new IllegalArgumentException("内容点索引必须在0-99之间，当前值: " + contentPointIndex);
        }
        if (sentenceIndex < 0 || sentenceIndex > 99) {
            throw new IllegalArgumentException("句子索引必须在0-99之间，当前值: " + sentenceIndex);
        }
        
        int globalIndex = pageNumber * 10000 + contentPointIndex * 100 + sentenceIndex;
        
        log.debug("生成全局片段索引: 页码={}, 内容点={}, 句子={} -> 全局索引={}", 
                pageNumber, contentPointIndex, sentenceIndex, globalIndex);
        
        return globalIndex;
    }
    
    /**
     * 从全局索引解析出页码、内容点索引和句子索引
     * 
     * @param globalIndex 全局片段索引
     * @return 解析后的索引信息
     */
    public static SegmentIndexInfo parseSegmentIndex(int globalIndex) {
        if (globalIndex < 0) {
            throw new IllegalArgumentException("全局索引不能为负数，当前值: " + globalIndex);
        }
        
        int pageNumber = globalIndex / 10000;
        int contentPointIndex = (globalIndex % 10000) / 100;
        int sentenceIndex = globalIndex % 100;
        
        SegmentIndexInfo info = new SegmentIndexInfo(pageNumber, contentPointIndex, sentenceIndex);
        
        log.debug("解析全局片段索引: 全局索引={} -> 页码={}, 内容点={}, 句子={}", 
                globalIndex, pageNumber, contentPointIndex, sentenceIndex);
        
        return info;
    }
    
    /**
     * 从全局索引提取页码
     * 
     * @param globalIndex 全局片段索引
     * @return 页码
     */
    public static int extractPageNumber(int globalIndex) {
        return globalIndex / 10000;
    }
    
    /**
     * 从全局索引提取页面内的片段索引
     * 
     * @param globalIndex 全局片段索引
     * @return 页面内的片段索引（内容点索引 * 100 + 句子索引）
     */
    public static int extractPageSegmentIndex(int globalIndex) {
        return globalIndex % 10000;
    }
    
    /**
     * 验证全局索引的有效性
     */
    public static boolean isValidGlobalIndex(int globalIndex) {
        try {
            SegmentIndexInfo info = parseSegmentIndex(globalIndex);
            return info.getPageNumber() >= 0 && info.getPageNumber() <= 999 &&
                   info.getContentPointIndex() >= 0 && info.getContentPointIndex() <= 99 &&
                   info.getSentenceIndex() >= 0 && info.getSentenceIndex() <= 99;
        } catch (Exception e) {
            log.warn("无效的全局索引: {}", globalIndex, e);
            return false;
        }
    }
    
    /**
     * 片段索引信息
     */
    @Data
    @AllArgsConstructor
    public static class SegmentIndexInfo {
        private int pageNumber;
        private int contentPointIndex;
        private int sentenceIndex;
        
        @Override
        public String toString() {
            return String.format("页码:%d, 内容点:%d, 句子:%d", pageNumber, contentPointIndex, sentenceIndex);
        }
    }
}