package com.treepeople.leapmindtts.service.admin.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.treepeople.leapmindtts.mapper.AudioSegmentMapper;
import com.treepeople.leapmindtts.pojo.entity.AudioSegment;
import com.treepeople.leapmindtts.service.admin.AudioSegmentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 音频片段服务实现类
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AudioSegmentServiceImpl extends ServiceImpl<AudioSegmentMapper, AudioSegment> implements AudioSegmentService {

    private final AudioSegmentMapper audioSegmentMapper;

    @Override
    public AudioSegment getBySessionAndIndex(String courseId, Integer segmentIndex) {
        return audioSegmentMapper.selectBySessionAndIndex(courseId, segmentIndex);
    }

    @Override
    public List<AudioSegment> getByCourseId(String courseId) {
        return audioSegmentMapper.selectByCourseId(courseId);
    }

    @Override
    public List<AudioSegment> getFromSegmentIndex(String courseId, Integer startIndex) {
        return audioSegmentMapper.selectFromSegmentIndex(courseId, startIndex);
    }

    @Override
    public boolean saveAudioSegment(String courseId, Integer segmentIndex, String textContent,
                                  byte[] audioData, String audioFormat, Integer sampleRate) {
        try {
            // 计算音频数据校验和
            String checksum = calculateChecksum(audioData);

            AudioSegment segment = AudioSegment.builder()
                    .courseId(courseId)
                    .segmentIndex(segmentIndex)
                    .textContent(textContent)
                    .audioData(audioData)
                    .audioSize((long) audioData.length)
                    .audioFormat(audioFormat != null ? audioFormat : "wav")
                    .sampleRate(sampleRate != null ? sampleRate : 16000)
                    .checksum(checksum)
                    .createdAt(LocalDateTime.now())
                    .build();

            boolean result = save(segment);
            if (result) {
                log.info("保存音频片段成功，会话ID: {}, 片段索引: {}, 音频大小: {} bytes",
                        courseId, segmentIndex, audioData.length);
            } else {
                log.error("保存音频片段失败，会话ID: {}, 片段索引: {}", courseId, segmentIndex);
            }
            return result;
        } catch (Exception e) {
            log.error("保存音频片段异常，会话ID: {}, 片段索引: {}", courseId, segmentIndex, e);
            return false;
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean saveBatchAudioSegments(List<AudioSegment> segments) {
        try {
            // 为每个片段计算校验和和设置创建时间
            for (AudioSegment segment : segments) {
                if (segment.getAudioData() != null) {
                    segment.setChecksum(calculateChecksum(segment.getAudioData()));
                    segment.setAudioSize((long) segment.getAudioData().length);
                }
                if (segment.getCreatedAt() == null) {
                    segment.setCreatedAt(LocalDateTime.now());
                }
                if (segment.getAudioFormat() == null) {
                    segment.setAudioFormat("wav");
                }
                if (segment.getSampleRate() == null) {
                    segment.setSampleRate(16000);
                }
            }

            boolean result = saveBatch(segments);
            if (result) {
                log.info("批量保存音频片段成功，数量: {}", segments.size());
            } else {
                log.error("批量保存音频片段失败，数量: {}", segments.size());
            }
            return result;
        } catch (Exception e) {
            log.error("批量保存音频片段异常", e);
            throw e;
        }
    }

    @Override
    public boolean deleteByCourseId(String courseId) {
        int result = audioSegmentMapper.deleteByCourseId(courseId);
        if (result > 0) {
            log.info("删除会话音频片段成功，会话ID: {}, 删除数量: {}", courseId, result);
        } else {
            log.info("没有找到要删除的音频片段，会话ID: {}", courseId);
        }
        return result >= 0; // 即使没有删除任何记录也认为是成功的
    }

    @Override
    public int countByCourseId(String courseId) {
        return audioSegmentMapper.countByCourseId(courseId);
    }

    @Override
    public Long getTotalAudioSize(String courseId) {
        return audioSegmentMapper.getTotalAudioSizeByCourseId(courseId);
    }

    @Override
    public Long getTotalDuration(String courseId) {
        return audioSegmentMapper.getTotalDurationByCourseId(courseId);
    }

    @Override
    public boolean segmentExists(String courseId, Integer segmentIndex) {
        AudioSegment segment = getBySessionAndIndex(courseId, segmentIndex);
        return segment != null;
    }

    @Override
    public boolean updateChecksum(String courseId, Integer segmentIndex, String checksum) {
        AudioSegment segment = getBySessionAndIndex(courseId, segmentIndex);
        if (segment != null) {
            segment.setChecksum(checksum);
            boolean result = updateById(segment);
            if (result) {
                log.debug("更新音频片段校验和成功，会话ID: {}, 片段索引: {}", courseId, segmentIndex);
            } else {
                log.warn("更新音频片段校验和失败，会话ID: {}, 片段索引: {}", courseId, segmentIndex);
            }
            return result;
        }
        return false;
    }

    // ========== PPT相关方法实现 ==========

    @Override
    public boolean saveAudioSegment(AudioSegment segment) {
        try {
            // 设置默认值和计算校验和
            if (segment.getAudioData() != null) {
                segment.setChecksum(calculateChecksum(segment.getAudioData()));
                if (segment.getAudioSize() == null) {
                    segment.setAudioSize((long) segment.getAudioData().length);
                }
            }
            if (segment.getCreatedAt() == null) {
                segment.setCreatedAt(LocalDateTime.now());
            }
            if (segment.getAudioFormat() == null) {
                segment.setAudioFormat("wav");
            }
            if (segment.getSampleRate() == null) {
                segment.setSampleRate(16000);
            }

            boolean result = save(segment);
            if (result) {
                log.info("保存音频片段成功，会话ID: {}, 片段索引: {}, 页码: {}",
                        segment.getCourseId(), segment.getSegmentIndex(), segment.getSlidePageNumber());
            } else {
                log.error("保存音频片段失败，会话ID: {}, 片段索引: {}",
                        segment.getCourseId(), segment.getSegmentIndex());
            }
            return result;
        } catch (Exception e) {
            log.error("保存音频片段异常，会话ID: {}, 片段索引: {}",
                    segment.getCourseId(), segment.getSegmentIndex(), e);
            return false;
        }
    }

    @Override
    public List<AudioSegment> getBySessionAndSlide(String courseId, Integer pageNumber) {
        try {
            List<AudioSegment> segments = audioSegmentMapper.selectBySessionAndSlide(courseId, pageNumber);
            log.debug("查询页面音频片段，会话ID: {}, 页码: {}, 找到 {} 个片段",
                    courseId, pageNumber, segments.size());
            return segments;
        } catch (Exception e) {
            log.error("查询页面音频片段失败，会话ID: {}, 页码: {}", courseId, pageNumber, e);
            return List.of();
        }
    }

    @Override
    public List<AudioSegment> getBySessionSlideAndContentPoint(String courseId, Integer pageNumber, Integer contentPointIndex) {
        try {
            List<AudioSegment> segments = audioSegmentMapper.selectBySessionSlideAndContentPoint(
                    courseId, pageNumber, contentPointIndex);
            log.debug("查询内容点音频片段，会话ID: {}, 页码: {}, 内容点: {}, 找到 {} 个片段",
                    courseId, pageNumber, contentPointIndex, segments.size());
            return segments;
        } catch (Exception e) {
            log.error("查询内容点音频片段失败，会话ID: {}, 页码: {}, 内容点: {}",
                    courseId, pageNumber, contentPointIndex, e);
            return List.of();
        }
    }

    @Override
    public boolean isSessionExist(String courseId) {
        int count = audioSegmentMapper.countByCourseId(courseId);
        return count > 0;
    }

    @Override
    public boolean isAudioSynthesisExist(String courseId) {
        QueryWrapper<AudioSegment> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("course_id", courseId);
        List<AudioSegment> audioSegmentList = list(queryWrapper);
        if (audioSegmentList.isEmpty()) {
            return false;
        }
        for (AudioSegment audioSegment : audioSegmentList){
            if (audioSegment.getAudioData() == null || audioSegment.getAudioData().length == 0) {
                return false;
            }
        }

        return true;
    }


    /**
     * 计算音频数据的MD5校验和
     */
    private String calculateChecksum(byte[] audioData) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(audioData);
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            log.warn("计算音频数据校验和失败", e);
            return null;
        }
    }
}
