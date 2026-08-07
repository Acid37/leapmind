package com.treepeople.leapmindtts.service.lesson.impl;

import com.treepeople.leapmindtts.mapper.LectureMapper;
import com.treepeople.leapmindtts.pojo.dto.LectureProgressDTO;
import com.treepeople.leapmindtts.pojo.entity.Lecture;
import com.treepeople.leapmindtts.service.lesson.LectureProgressService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * M4 讲课进度 Service 实现（许沣睿）
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(rollbackFor = Exception.class)
public class LectureProgressServiceImpl implements LectureProgressService {

    private final LectureMapper lectureMapper;

    @Override
    public void saveProgress(LectureProgressDTO progressDTO) {
        int rows = lectureMapper.updateProgress(
                progressDTO.getCourseId(),
                progressDTO.getCurrentPage(),
                progressDTO.getProgressMs(),
                progressDTO.getStatus());
        if (rows == 0) {
            throw new RuntimeException("讲课内容不存在，无法保存进度: courseId=" + progressDTO.getCourseId());
        }
        log.info("保存讲课进度: courseId={}, page={}, progressMs={}, status={}",
                progressDTO.getCourseId(), progressDTO.getCurrentPage(),
                progressDTO.getProgressMs(), progressDTO.getStatus());
    }

    @Override
    public LectureProgressDTO getProgress(String courseId) {
        Lecture entity = lectureMapper.selectByCourseId(courseId);
        if (entity == null) {
            throw new RuntimeException("讲课内容不存在: courseId=" + courseId);
        }
        LectureProgressDTO dto = new LectureProgressDTO();
        dto.setCourseId(entity.getCourseId());
        dto.setCurrentPage(entity.getCurrentPage());
        dto.setProgressMs(entity.getProgressMs());
        dto.setStatus(entity.getStatus());
        return dto;
    }

    @Override
    public void savePlaybackSnapshot(String courseId, String snapshotJson) {
        int rows = lectureMapper.updatePlaybackSnapshot(courseId, snapshotJson);
        if (rows == 0) {
            throw new RuntimeException("讲课内容不存在，无法保存回放快照: courseId=" + courseId);
        }
        log.info("保存回放快照: courseId={}, snapshotSize={} chars",
                courseId, snapshotJson != null ? snapshotJson.length() : 0);
    }

    @Override
    public String getPlaybackSnapshot(String courseId) {
        Lecture entity = lectureMapper.selectByCourseId(courseId);
        if (entity == null) {
            throw new RuntimeException("讲课内容不存在: courseId=" + courseId);
        }
        return entity.getPlaybackSnapshot();
    }
}
