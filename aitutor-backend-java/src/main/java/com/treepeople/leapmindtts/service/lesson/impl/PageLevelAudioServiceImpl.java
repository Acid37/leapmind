package com.treepeople.leapmindtts.service.lesson.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.treepeople.leapmindtts.mapper.AudioSegmentMapper;
import com.treepeople.leapmindtts.pojo.dto.PPTAudioSegment;
import com.treepeople.leapmindtts.pojo.entity.AudioSegment;
import com.treepeople.leapmindtts.service.lesson.PageLevelAudioService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 页面级音频服务实现类
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PageLevelAudioServiceImpl implements PageLevelAudioService {

    private final AudioSegmentMapper audioSegmentMapper;

    @Override
    public boolean savePageAudio(String courseId, Integer pageNumber, String pageTitle,
                               String slideType, String slideDescription,
                               List<PPTAudioSegment> audioSegments,
                               String audioFormat, Integer sampleRate) {
        // 这里应该实现页面级音频保存逻辑
        // 暂时返回true，实际实现需要根据具体的数据库结构来完成
        log.info("保存页面音频，会话ID: {}, 页码: {}, 片段数: {}", courseId, pageNumber, audioSegments.size());
        return true;
    }

    @Override
    public AudioSegment getPageAudio(String courseId, Integer pageNumber) {
        try {
            QueryWrapper<AudioSegment> queryWrapper = new QueryWrapper<>();
            queryWrapper.eq("course_id", courseId)
                       .eq("slide_page_number", pageNumber)
                       .orderByAsc("segment_index")  // 按片段索引排序
                       .last("LIMIT 1");  // 只取第一条（如果有多条）

            AudioSegment segment = audioSegmentMapper.selectOne(queryWrapper);
            if (segment != null) {
                log.debug("查询页面音频成功，会话ID: {}, 页码: {}", courseId, pageNumber);
            } else {
                log.warn("未找到页面音频，会话ID: {}, 页码: {}", courseId, pageNumber);
            }
            return segment;
        } catch (Exception e) {
            log.error("查询页面音频失败，会话ID: {}, 页码: {}", courseId, pageNumber, e);
            return null;
        }
    }

    @Override
    public byte[] getPageAudioSegment(String courseId, Integer pageNumber, Integer segmentIndex) {
        // 实现获取页面内特定片段的逻辑
        log.debug("获取页面音频片段，会话ID: {}, 页码: {}, 片段索引: {}", courseId, pageNumber, segmentIndex);
        return new byte[0];
    }

    @Override
    public List<AudioSegment> getSessionPageAudios(String courseId) {
        try {
            QueryWrapper<AudioSegment> queryWrapper = new QueryWrapper<>();
            queryWrapper.eq("course_id", courseId)
                       .orderByAsc("slide_page_number");

            List<AudioSegment> segments = audioSegmentMapper.selectList(queryWrapper);
            log.info("查询会话页面音频，会话ID: {}, 找到 {} 个页面", courseId, segments.size());
            return segments;
        } catch (Exception e) {
            log.error("查询会话页面音频失败，会话ID: {}", courseId, e);
            return List.of();
        }
    }

    @Override
    public List<PPTAudioSegment> getPageAudioSegments(String courseId, Integer pageNumber) {
        try {
            QueryWrapper<AudioSegment> queryWrapper = new QueryWrapper<>();
            queryWrapper.eq("course_id", courseId)
                       .eq("slide_page_number", pageNumber)
                       .eq("segment_status", "AUDIO_GENERATED"); // 只查询已生成音频的

            List<AudioSegment> segments = audioSegmentMapper.selectList(queryWrapper);

            // 转换为PPTAudioSegment
            List<PPTAudioSegment> result = segments.stream()
                    .map(this::convertToPPTAudioSegment)
                    .collect(Collectors.toList());

            log.info("查询页面音频片段，会话ID: {}, 页码: {}, 找到 {} 个片段", courseId, pageNumber, result.size());
            return result;
        } catch (Exception e) {
            log.error("查询页面音频片段失败，会话ID: {}, 页码: {}", courseId, pageNumber, e);
            return List.of();
        }
    }

    @Override
    public byte[] getAudioSegmentByGlobalIndex(String courseId, Integer globalSegmentIndex) {
        try {
            QueryWrapper<AudioSegment> queryWrapper = new QueryWrapper<>();
            queryWrapper.eq("course_id", courseId)
                       .eq("segment_index", globalSegmentIndex);

            AudioSegment segment = audioSegmentMapper.selectOne(queryWrapper);
            if (segment != null && segment.getAudioData() != null) {
                log.debug("获取音频片段成功，会话ID: {}, 全局索引: {}", courseId, globalSegmentIndex);
                return segment.getAudioData();
            } else {
                log.warn("未找到音频片段，会话ID: {}, 全局索引: {}", courseId, globalSegmentIndex);
                return new byte[0];
            }
        } catch (Exception e) {
            log.error("获取音频片段失败，会话ID: {}, 全局索引: {}", courseId, globalSegmentIndex, e);
            return new byte[0];
        }
    }

    @Override
    public PPTAudioSegment getAudioSegmentInfoByGlobalIndex(String courseId, Integer globalSegmentIndex) {
        try {
            QueryWrapper<AudioSegment> queryWrapper = new QueryWrapper<>();
            queryWrapper.eq("course_id", courseId)
                       .eq("segment_index", globalSegmentIndex);

            AudioSegment segment = audioSegmentMapper.selectOne(queryWrapper);
            if (segment != null) {
                log.debug("获取音频片段信息成功，会话ID: {}, 全局索引: {}", courseId, globalSegmentIndex);
                return convertToPPTAudioSegment(segment);
            } else {
                log.warn("未找到音频片段信息，会话ID: {}, 全局索引: {}", courseId, globalSegmentIndex);
                return null;
            }
        } catch (Exception e) {
            log.error("获取音频片段信息失败，会话ID: {}, 全局索引: {}", courseId, globalSegmentIndex, e);
            return null;
        }
    }

    @Override
    public boolean deletePageAudio(String courseId, Integer pageNumber) {
        try {
            QueryWrapper<AudioSegment> queryWrapper = new QueryWrapper<>();
            queryWrapper.eq("course_id", courseId)
                       .eq("slide_page_number", pageNumber);

            int result = audioSegmentMapper.delete(queryWrapper);
            if (result > 0) {
                log.info("删除页面音频成功，会话ID: {}, 页码: {}, 删除 {} 条记录", courseId, pageNumber, result);
            } else {
                log.warn("删除页面音频失败，会话ID: {}, 页码: {}", courseId, pageNumber);
            }
            return result > 0;
        } catch (Exception e) {
            log.error("删除页面音频异常，会话ID: {}, 页码: {}", courseId, pageNumber, e);
            return false;
        }
    }

    @Override
    public int deleteSessionPageAudios(String courseId) {
        try {
            QueryWrapper<AudioSegment> queryWrapper = new QueryWrapper<>();
            queryWrapper.eq("course_id", courseId);

            int result = audioSegmentMapper.delete(queryWrapper);
            log.info("删除会话页面音频，会话ID: {}, 删除 {} 条记录", courseId, result);
            return result;
        } catch (Exception e) {
            log.error("删除会话页面音频异常，会话ID: {}", courseId, e);
            return 0;
        }
    }

    @Override
    public long[] getSessionAudioStats(String courseId) {
        try {
            QueryWrapper<AudioSegment> queryWrapper = new QueryWrapper<>();
            queryWrapper.eq("course_id", courseId)
                       .eq("segment_status", "AUDIO_GENERATED");

            List<AudioSegment> segments = audioSegmentMapper.selectList(queryWrapper);

            long totalAudioSize = segments.stream()
                    .mapToLong(segment -> segment.getAudioSize() != null ? segment.getAudioSize() : 0L)
                    .sum();

            long totalDuration = segments.stream()
                    .mapToLong(segment -> segment.getDuration() != null ? segment.getDuration() : 0L)
                    .sum();

            long totalSegments = segments.size();

            log.info("获取会话音频统计，会话ID: {}, 总大小: {} bytes, 总时长: {} ms, 总片段数: {}",
                    courseId, totalAudioSize, totalDuration, totalSegments);

            return new long[]{totalAudioSize, totalDuration, totalSegments};
        } catch (Exception e) {
            log.error("获取会话音频统计失败，会话ID: {}", courseId, e);
            return new long[]{0, 0, 0};
        }
    }

    @Override
    public byte[] getPageAudioData(String courseId, Integer pageNumber) {
        try {
            // 获取该页面的所有音频片段（按片段索引排序）
            List<PPTAudioSegment> segments = getPageAudioSegments(courseId, pageNumber);

            if (segments == null || segments.isEmpty()) {
                log.warn("未找到页面音频数据，会话ID: {}, 页码: {}", courseId, pageNumber);
                return new byte[0];
            }

            log.info("获取页面音频数据，会话ID: {}, 页码: {}, 片段数: {}",
                    courseId, pageNumber, segments.size());

            // 如果只有一个片段，直接返回
            if (segments.size() == 1) {
                byte[] audioData = segments.get(0).getAudioData();
                if (audioData != null) {
                    log.info("返回单个音频片段，大小: {} bytes", audioData.length);
                    return audioData;
                }
                return new byte[0];
            }

            // 多个片段，需要合并音频数据
            log.info("开始合并 {} 个音频片段", segments.size());

            // 计算总大小
            int totalSize = segments.stream()
                    .filter(s -> s.getAudioData() != null)
                    .mapToInt(s -> s.getAudioData().length)
                    .sum();

            if (totalSize == 0) {
                log.warn("所有片段音频数据为空");
                return new byte[0];
            }

            // 合并所有音频数据（按segment_index顺序）
            byte[] mergedAudio = new byte[totalSize];
            int offset = 0;

            for (PPTAudioSegment segment : segments) {
                if (segment.getAudioData() != null && segment.getAudioData().length > 0) {
                    System.arraycopy(segment.getAudioData(), 0, mergedAudio, offset, segment.getAudioData().length);
                    offset += segment.getAudioData().length;
                    log.debug("合并片段 {}, 大小: {} bytes, 累计偏移: {}",
                            segment.getSegmentIndex(), segment.getAudioData().length, offset);
                }
            }

            log.info("音频合并完成，总大小: {} bytes", mergedAudio.length);
            return mergedAudio;

        } catch (Exception e) {
            log.error("获取页面音频数据失败，会话ID: {}, 页码: {}", courseId, pageNumber, e);
            return new byte[0];
        }
    }

    @Override
    public List<PPTAudioSegment> getPageSegmentMetadata(String courseId, Integer pageNumber) {
        // 获取页面的片段元数据（不包含音频数据）
        return getPageAudioSegments(courseId, pageNumber).stream()
                .peek(segment -> segment.setAudioData(null)) // 移除音频数据
                .collect(Collectors.toList());
    }

    // 新增：支持审核流程的方法实现

    @Override
    public boolean saveTextOnlySegments(String courseId, Integer pageNumber, String pageTitle,
                                      String slideType, String slideDescription,
                                      List<PPTAudioSegment> textSegments) {
        try {
            for (PPTAudioSegment textSegment : textSegments) {
                AudioSegment segment = AudioSegment.builder()
                        .courseId(courseId)
                        .segmentIndex(textSegment.getSegmentIndex())
                        .textContent(textSegment.getTextContent())
                        .audioData(null) // 不包含音频数据
                        .audioSize(0L)
                        .duration(0L)
                        .audioFormat(textSegment.getAudioFormat())
                        .sampleRate(textSegment.getSampleRate())
                        .slidePageNumber(pageNumber)
                        .slideTitle(pageTitle)
                        .slideType(slideType)
                        .slideDescription(slideDescription)
                        .originalText(textSegment.getOriginalText())
                        .polishedText(textSegment.getPolishedText())
                        .segmentStatus("TEXT_ONLY") // 标记为仅文本状态
                        .createdAt(LocalDateTime.now())
                        .build();

                int result = audioSegmentMapper.insert(segment);
                if (result <= 0) {
                    log.error("保存文本片段失败，会话ID: {}, 片段索引: {}", courseId, textSegment.getSegmentIndex());
                    return false;
                }
            }

            log.info("保存文本片段成功，会话ID: {}, 页码: {}, 片段数: {}", courseId, pageNumber, textSegments.size());
            return true;
        } catch (Exception e) {
            log.error("保存文本片段异常，会话ID: {}, 页码: {}", courseId, pageNumber, e);
            return false;
        }
    }

    @Override
    public List<AudioSegment> getTextOnlySegments(String courseId) {
        try {
            QueryWrapper<AudioSegment> queryWrapper = new QueryWrapper<>();
            queryWrapper.eq("course_id", courseId)
                       .eq("segment_status", "TEXT_ONLY")
                       .orderByAsc("slide_page_number", "segment_index");

            List<AudioSegment> segments = audioSegmentMapper.selectList(queryWrapper);
            log.info("查询文本片段，会话ID: {}, 找到 {} 个片段", courseId, segments.size());
            return segments;
        } catch (Exception e) {
            log.error("查询文本片段失败，会话ID: {}", courseId, e);
            return List.of();
        }
    }

    @Override
    public boolean deleteTextOnlySegments(String courseId) {
        try {
            QueryWrapper<AudioSegment> queryWrapper = new QueryWrapper<>();
            queryWrapper.eq("course_id", courseId)
                       .eq("segment_status", "TEXT_ONLY");

            int result = audioSegmentMapper.delete(queryWrapper);
            log.info("删除文本片段，会话ID: {}, 删除数量: {}", courseId, result);
            return true;
        } catch (Exception e) {
            log.error("删除文本片段失败，会话ID: {}", courseId, e);
            return false;
        }
    }

    @Override
    public boolean updateSegmentsWithAudio(String courseId, Integer pageNumber, List<PPTAudioSegment> audioSegments) {
        try {
            for (PPTAudioSegment audioSegment : audioSegments) {
                // 更新对应的文本片段，添加音频数据
                QueryWrapper<AudioSegment> queryWrapper = new QueryWrapper<>();
                queryWrapper.eq("course_id", courseId)
                           .eq("segment_index", audioSegment.getSegmentIndex());

                AudioSegment existingSegment = audioSegmentMapper.selectOne(queryWrapper);
                if (existingSegment != null) {
                    existingSegment.setAudioData(audioSegment.getAudioData());
                    existingSegment.setAudioSize(audioSegment.getAudioSize());
                    existingSegment.setDuration(audioSegment.getDuration());
                    existingSegment.setChecksum(audioSegment.getChecksum());
                    existingSegment.setSegmentStatus("AUDIO_GENERATED"); // 更新状态

                    int result = audioSegmentMapper.updateById(existingSegment);
                    if (result <= 0) {
                        log.error("更新音频片段失败，会话ID: {}, 片段索引: {}", courseId, audioSegment.getSegmentIndex());
                        return false;
                    }
                } else {
                    log.warn("未找到对应的文本片段，会话ID: {}, 片段索引: {}", courseId, audioSegment.getSegmentIndex());
                }
            }

            log.info("更新音频片段成功，会话ID: {}, 页码: {}, 片段数: {}", courseId, pageNumber, audioSegments.size());
            return true;
        } catch (Exception e) {
            log.error("更新音频片段异常，会话ID: {}, 页码: {}", courseId, pageNumber, e);
            return false;
        }
    }

    /**
     * 转换AudioSegment为PPTAudioSegment
     */
    private PPTAudioSegment convertToPPTAudioSegment(AudioSegment segment) {
        return PPTAudioSegment.builder()
                .courseId(segment.getCourseId())
                .slidePageNumber(segment.getSlidePageNumber())
                .slideTitle(segment.getSlideTitle())
                .contentPointIndex(0) // 需要根据实际情况设置
                .segmentIndex(segment.getSegmentIndex())
                .slideType(segment.getSlideType())
                .slideDescription(segment.getSlideDescription())
                .originalText(segment.getOriginalText())
                .polishedText(segment.getPolishedText())
                .textContent(segment.getTextContent())
                .audioData(segment.getAudioData())
                .audioSize(segment.getAudioSize())
                .duration(segment.getDuration())
                .audioFormat(segment.getAudioFormat())
                .sampleRate(segment.getSampleRate())
                .checksum(segment.getChecksum())
                .createdAt(segment.getCreatedAt())
                .build();
    }
}
