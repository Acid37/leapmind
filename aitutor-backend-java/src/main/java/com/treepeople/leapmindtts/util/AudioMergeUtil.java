package com.treepeople.leapmindtts.util;

import lombok.extern.slf4j.Slf4j;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * 音频合并工具类
 * 用于将多个音频片段合并为一个音频文件，并支持分割操作
 */
@Slf4j
public class AudioMergeUtil {
    
    // 音频片段分隔符（使用特殊的字节序列作为分隔符）
    private static final byte[] AUDIO_SEPARATOR = {
        (byte) 0xFF, (byte) 0xFE, (byte) 0xFD, (byte) 0xFC,  // 分隔符标识
        (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00   // 4字节长度占位符
    };
    
    /**
     * 合并多个音频片段为一个字节数组
     * 格式：[音频1][分隔符+长度][音频2][分隔符+长度]...[音频N]
     * 
     * @param audioSegments 音频片段列表
     * @return 合并后的音频字节数组
     */
    public static byte[] mergeAudioSegments(List<byte[]> audioSegments) {
        if (audioSegments == null || audioSegments.isEmpty()) {
            log.warn("音频片段列表为空，返回空数组");
            return new byte[0];
        }
        
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            for (int i = 0; i < audioSegments.size(); i++) {
                byte[] audioData = audioSegments.get(i);
                
                if (audioData == null || audioData.length == 0) {
                    log.warn("跳过空的音频片段，索引: {}", i);
                    continue;
                }
                
                // 写入音频数据
                outputStream.write(audioData);
                
                // 如果不是最后一个片段，添加分隔符
                if (i < audioSegments.size() - 1) {
                    byte[] separator = createSeparator(audioData.length);
                    outputStream.write(separator);
                }
            }
            
            byte[] result = outputStream.toByteArray();
            log.info("音频合并完成，原始片段数: {}, 合并后大小: {} bytes", audioSegments.size(), result.length);
            return result;
            
        } catch (IOException e) {
            log.error("音频合并失败", e);
            throw new RuntimeException("音频合并失败", e);
        }
    }
    
    /**
     * 分割合并的音频数据为独立的音频片段
     * 
     * @param mergedAudioData 合并的音频数据
     * @return 分割后的音频片段列表
     */
    public static List<byte[]> splitAudioSegments(byte[] mergedAudioData) {
        List<byte[]> segments = new ArrayList<>();
        
        if (mergedAudioData == null || mergedAudioData.length == 0) {
            log.warn("合并音频数据为空，返回空列表");
            return segments;
        }
        
        int currentPos = 0;
        int segmentIndex = 0;
        
        while (currentPos < mergedAudioData.length) {
            // 查找下一个分隔符位置
            int separatorPos = findNextSeparator(mergedAudioData, currentPos);
            
            if (separatorPos == -1) {
                // 没有找到分隔符，说明这是最后一个片段
                byte[] lastSegment = new byte[mergedAudioData.length - currentPos];
                System.arraycopy(mergedAudioData, currentPos, lastSegment, 0, lastSegment.length);
                segments.add(lastSegment);
                log.debug("提取最后一个音频片段，索引: {}, 大小: {} bytes", segmentIndex, lastSegment.length);
                break;
            }
            
            // 提取当前片段
            int segmentLength = separatorPos - currentPos;
            byte[] segment = new byte[segmentLength];
            System.arraycopy(mergedAudioData, currentPos, segment, 0, segmentLength);
            segments.add(segment);
            log.debug("提取音频片段，索引: {}, 大小: {} bytes", segmentIndex, segment.length);
            
            // 移动到下一个片段的开始位置（跳过分隔符）
            currentPos = separatorPos + AUDIO_SEPARATOR.length;
            segmentIndex++;
        }
        
        log.info("音频分割完成，总片段数: {}", segments.size());
        return segments;
    }
    
    /**
     * 从合并的音频数据中提取指定索引的音频片段
     * 
     * @param mergedAudioData 合并的音频数据
     * @param segmentIndex 片段索引（从0开始）
     * @return 指定的音频片段，如果索引无效返回null
     */
    public static byte[] extractAudioSegment(byte[] mergedAudioData, int segmentIndex) {
        if (segmentIndex < 0) {
            log.warn("音频片段索引无效: {}", segmentIndex);
            return null;
        }
        
        List<byte[]> segments = splitAudioSegments(mergedAudioData);
        
        if (segmentIndex >= segments.size()) {
            log.warn("音频片段索引超出范围: {}, 总片段数: {}", segmentIndex, segments.size());
            return null;
        }
        
        return segments.get(segmentIndex);
    }
    
    /**
     * 获取合并音频数据中的片段数量
     * 
     * @param mergedAudioData 合并的音频数据
     * @return 片段数量
     */
    public static int getSegmentCount(byte[] mergedAudioData) {
        if (mergedAudioData == null || mergedAudioData.length == 0) {
            return 0;
        }
        
        int count = 1; // 至少有一个片段
        int currentPos = 0;
        
        while (currentPos < mergedAudioData.length) {
            int separatorPos = findNextSeparator(mergedAudioData, currentPos);
            if (separatorPos == -1) {
                break;
            }
            count++;
            currentPos = separatorPos + AUDIO_SEPARATOR.length;
        }
        
        return count;
    }
    
    /**
     * 创建包含长度信息的分隔符
     * 
     * @param audioLength 前一个音频片段的长度
     * @return 包含长度信息的分隔符
     */
    private static byte[] createSeparator(int audioLength) {
        byte[] separator = new byte[AUDIO_SEPARATOR.length];
        System.arraycopy(AUDIO_SEPARATOR, 0, separator, 0, 4); // 复制分隔符标识
        
        // 将长度信息写入后4个字节（大端序）
        separator[4] = (byte) ((audioLength >> 24) & 0xFF);
        separator[5] = (byte) ((audioLength >> 16) & 0xFF);
        separator[6] = (byte) ((audioLength >> 8) & 0xFF);
        separator[7] = (byte) (audioLength & 0xFF);
        
        return separator;
    }
    
    /**
     * 查找下一个分隔符的位置
     * 
     * @param data 数据数组
     * @param startPos 开始搜索的位置
     * @return 分隔符位置，如果没找到返回-1
     */
    private static int findNextSeparator(byte[] data, int startPos) {
        for (int i = startPos; i <= data.length - AUDIO_SEPARATOR.length; i++) {
            boolean found = true;
            
            // 检查分隔符标识（前4个字节）
            for (int j = 0; j < 4; j++) {
                if (data[i + j] != AUDIO_SEPARATOR[j]) {
                    found = false;
                    break;
                }
            }
            
            if (found) {
                return i;
            }
        }
        
        return -1;
    }
    
    /**
     * 验证合并音频数据的完整性
     * 
     * @param mergedAudioData 合并的音频数据
     * @return 验证结果
     */
    public static boolean validateMergedAudio(byte[] mergedAudioData) {
        if (mergedAudioData == null || mergedAudioData.length == 0) {
            return false;
        }
        
        try {
            List<byte[]> segments = splitAudioSegments(mergedAudioData);
            return !segments.isEmpty() && segments.stream().allMatch(segment -> segment.length > 0);
        } catch (Exception e) {
            log.error("验证合并音频数据失败", e);
            return false;
        }
    }
}