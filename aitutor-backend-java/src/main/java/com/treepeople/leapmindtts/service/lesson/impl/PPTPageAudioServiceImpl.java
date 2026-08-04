package com.treepeople.leapmindtts.service.lesson.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.treepeople.leapmindtts.mapper.PPTPageAudioMapper;
import com.treepeople.leapmindtts.pojo.dto.PPTAudioSegment;
import com.treepeople.leapmindtts.pojo.entity.PPTPageAudio;
import com.treepeople.leapmindtts.service.lesson.PPTPageAudioService;
import com.treepeople.leapmindtts.util.AudioMergeUtil;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * PPT页面音频服务实现类
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PPTPageAudioServiceImpl implements PPTPageAudioService {

    private final PPTPageAudioMapper pptPageAudioMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean savePageAudio(String courseId, Integer pageNumber, String pageTitle,
                                String slideType, String slideDescription,
                                List<PPTAudioSegment> audioSegments,
                                String audioFormat, Integer sampleRate) {

        log.info("开始保存页面音频，会话ID: {}, 页码: {}, 片段数: {}", courseId, pageNumber, audioSegments.size());

        try {
            // 提取音频数据
            List<byte[]> audioDataList = audioSegments.stream()
                    .map(PPTAudioSegment::getAudioData)
                    .collect(Collectors.toList());

            // 合并音频数据
            byte[] mergedAudioData = AudioMergeUtil.mergeAudioSegments(audioDataList);

            // 计算统计信息
            long totalAudioSize = audioDataList.stream()
                    .mapToLong(data -> data != null ? data.length : 0)
                    .sum();

            long totalDuration = audioSegments.stream()
                    .mapToLong(segment -> segment.getDuration() != null ? segment.getDuration() : 0)
                    .sum();

            // 创建片段元数据
            String segmentMetadata = createSegmentMetadata(audioSegments);

            // 生成校验和
            String checksum = generateChecksum(mergedAudioData);

            // 检查是否已存在该页面的音频数据
            PPTPageAudio existingAudio = pptPageAudioMapper.selectBySessionAndPage(courseId, pageNumber);

            if (existingAudio != null) {
                // 更新现有记录
                existingAudio.setPageTitle(pageTitle);
                existingAudio.setSlideType(slideType);
                existingAudio.setSlideDescription(slideDescription);
                existingAudio.setSegmentCount(audioSegments.size());
                existingAudio.setMergedAudioData(mergedAudioData);
                existingAudio.setTotalAudioSize(totalAudioSize);
                existingAudio.setTotalDuration(totalDuration);
                existingAudio.setAudioFormat(audioFormat);
                existingAudio.setSampleRate(sampleRate);
                existingAudio.setSegmentMetadata(segmentMetadata);
                existingAudio.setChecksum(checksum);
                existingAudio.setUpdatedAt(LocalDateTime.now());

                int updated = pptPageAudioMapper.updateById(existingAudio);
                log.info("更新页面音频成功，会话ID: {}, 页码: {}, 影响行数: {}", courseId, pageNumber, updated);
                return updated > 0;

            } else {
                // 创建新记录
                PPTPageAudio pageAudio = PPTPageAudio.builder()
                        .courseId(courseId)
                        .pageNumber(pageNumber)
                        .pageTitle(pageTitle)
                        .slideType(slideType)
                        .slideDescription(slideDescription)
                        .segmentCount(audioSegments.size())
                        .mergedAudioData(mergedAudioData)
                        .totalAudioSize(totalAudioSize)
                        .totalDuration(totalDuration)
                        .audioFormat(audioFormat)
                        .sampleRate(sampleRate)
                        .segmentMetadata(segmentMetadata)
                        .checksum(checksum)
                        .createdAt(LocalDateTime.now())
                        .updatedAt(LocalDateTime.now())
                        .build();

                int inserted = pptPageAudioMapper.insert(pageAudio);
                log.info("保存页面音频成功，会话ID: {}, 页码: {}, 影响行数: {}", courseId, pageNumber, inserted);
                return inserted > 0;
            }

        } catch (Exception e) {
            log.error("保存页面音频失败，会话ID: {}, 页码: {}", courseId, pageNumber, e);
            throw new RuntimeException("保存页面音频失败", e);
        }
    }

    @Override
    public PPTPageAudio getPageAudio(String courseId, Integer pageNumber) {
        log.debug("查询页面音频，会话ID: {}, 页码: {}", courseId, pageNumber);
        return pptPageAudioMapper.selectBySessionAndPage(courseId, pageNumber);
    }

    @Override
    public byte[] getPageAudioSegment(String courseId, Integer pageNumber, Integer segmentIndex) {
        log.debug("获取页面音频片段，会话ID: {}, 页码: {}, 片段索引: {}", courseId, pageNumber, segmentIndex);

        PPTPageAudio pageAudio = getPageAudio(courseId, pageNumber);
        if (pageAudio == null || pageAudio.getMergedAudioData() == null) {
            log.warn("未找到页面音频数据，会话ID: {}, 页码: {}", courseId, pageNumber);
            return null;
        }

        return AudioMergeUtil.extractAudioSegment(pageAudio.getMergedAudioData(), segmentIndex);
    }

    @Override
    public List<PPTPageAudio> getSessionPageAudios(String courseId) {
        log.debug("查询会话的所有页面音频，会话ID: {}", courseId);
        return pptPageAudioMapper.selectByCourseId(courseId);
    }

    @Override
    public List<PPTAudioSegment> getPageAudioSegments(String courseId, Integer pageNumber) {
        log.debug("获取页面音频片段信息，会话ID: {}, 页码: {}", courseId, pageNumber);

        PPTPageAudio pageAudio = getPageAudio(courseId, pageNumber);
        if (pageAudio == null) {
            log.warn("未找到页面音频数据，会话ID: {}, 页码: {}", courseId, pageNumber);
            return new ArrayList<>();
        }

        try {
            // 解析片段元数据
            List<SegmentMetadata> metadataList = parseSegmentMetadata(pageAudio.getSegmentMetadata());

            // 分割音频数据
            List<byte[]> audioSegments = AudioMergeUtil.splitAudioSegments(pageAudio.getMergedAudioData());

            // 构建PPTAudioSegment列表
            List<PPTAudioSegment> segments = new ArrayList<>();
            for (int i = 0; i < metadataList.size() && i < audioSegments.size(); i++) {
                SegmentMetadata metadata = metadataList.get(i);
                byte[] audioData = audioSegments.get(i);

                PPTAudioSegment segment = PPTAudioSegment.builder()
                        .courseId(courseId)
                        .slidePageNumber(pageNumber)
                        .slideTitle(pageAudio.getPageTitle())
                        .contentPointIndex(metadata.getContentPointIndex())
                        .segmentIndex(metadata.getGlobalSegmentIndex())
                        .slideType(pageAudio.getSlideType())
                        .slideDescription(pageAudio.getSlideDescription())
                        .originalText(metadata.getOriginalText())
                        .polishedText(metadata.getPolishedText())
                        .textContent(metadata.getTextContent())
                        .audioData(audioData)
                        .audioSize((long) audioData.length)
                        .duration(metadata.getDuration())
                        .audioFormat(pageAudio.getAudioFormat())
                        .sampleRate(pageAudio.getSampleRate())
                        .checksum(generateChecksum(audioData))
                        .createdAt(pageAudio.getCreatedAt())
                        .build();

                segments.add(segment);
            }

            log.debug("成功获取页面音频片段信息，会话ID: {}, 页码: {}, 片段数: {}", courseId, pageNumber, segments.size());
            return segments;

        } catch (Exception e) {
            log.error("获取页面音频片段信息失败，会话ID: {}, 页码: {}", courseId, pageNumber, e);
            return new ArrayList<>();
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deletePageAudio(String courseId, Integer pageNumber) {
        log.info("删除页面音频，会话ID: {}, 页码: {}", courseId, pageNumber);

        QueryWrapper<PPTPageAudio> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("course_id", courseId).eq("page_number", pageNumber);

        int deleted = pptPageAudioMapper.delete(queryWrapper);
        log.info("删除页面音频完成，会话ID: {}, 页码: {}, 影响行数: {}", courseId, pageNumber, deleted);
        return deleted > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteSessionPageAudios(String courseId) {
        log.info("删除会话的所有页面音频，会话ID: {}", courseId);

        QueryWrapper<PPTPageAudio> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("course_id", courseId);

        int deleted = pptPageAudioMapper.delete(queryWrapper);
        log.info("删除会话页面音频完成，会话ID: {}, 影响行数: {}", courseId, deleted);
        return deleted;
    }

    @Override
    public long[] getSessionAudioStats(String courseId) {
        log.debug("获取会话音频统计信息，会话ID: {}", courseId);

        Long totalAudioSize = pptPageAudioMapper.getTotalAudioSize(courseId);
        Long totalDuration = pptPageAudioMapper.getTotalDuration(courseId);
        Integer totalSegmentCount = pptPageAudioMapper.getTotalSegmentCount(courseId);

        return new long[]{
            totalAudioSize != null ? totalAudioSize : 0,
            totalDuration != null ? totalDuration : 0,
            totalSegmentCount != null ? totalSegmentCount : 0
        };
    }

    /**
     * 创建片段元数据JSON
     */
    private String createSegmentMetadata(List<PPTAudioSegment> audioSegments) {
        try {
            List<SegmentMetadata> metadataList = audioSegments.stream()
                    .map(segment -> SegmentMetadata.builder()
                            .contentPointIndex(segment.getContentPointIndex())
                            .globalSegmentIndex(segment.getSegmentIndex())
                            .originalText(segment.getOriginalText())
                            .polishedText(segment.getPolishedText())
                            .textContent(segment.getTextContent())
                            .duration(segment.getDuration())
                            .audioSize(segment.getAudioSize())
                            .build())
                    .collect(Collectors.toList());

            return objectMapper.writeValueAsString(metadataList);
        } catch (JsonProcessingException e) {
            log.error("创建片段元数据失败", e);
            return "[]";
        }
    }

    /**
     * 解析片段元数据JSON
     */
    private List<SegmentMetadata> parseSegmentMetadata(String metadataJson) {
        try {
            if (metadataJson == null || metadataJson.trim().isEmpty()) {
                return new ArrayList<>();
            }

            return objectMapper.readValue(metadataJson,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, SegmentMetadata.class));
        } catch (JsonProcessingException e) {
            log.error("解析片段元数据失败", e);
            return new ArrayList<>();
        }
    }

    /**
     * 生成校验和
     */
    private String generateChecksum(byte[] data) {
        if (data == null || data.length == 0) {
            return "";
        }
        return String.valueOf(data.length) + "_" + String.valueOf(data.hashCode());
    }

    /**
     * 片段元数据内部类
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    private static class SegmentMetadata {
        private Integer contentPointIndex;
        private Integer globalSegmentIndex;
        private String originalText;
        private String polishedText;
        private String textContent;
        private Long duration;
        private Long audioSize;
    }
}
