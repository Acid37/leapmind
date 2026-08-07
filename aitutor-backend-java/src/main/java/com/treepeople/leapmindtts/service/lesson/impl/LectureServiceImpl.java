package com.treepeople.leapmindtts.service.lesson.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.treepeople.leapmindtts.mapper.LectureMapper;
import com.treepeople.leapmindtts.pojo.dto.LectureCreateRequest;
import com.treepeople.leapmindtts.pojo.entity.Lecture;
import com.treepeople.leapmindtts.pojo.enums.LectureStatus;
import com.treepeople.leapmindtts.pojo.vo.LectureVO;
import com.treepeople.leapmindtts.service.lesson.LectureService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * M4 讲课内容 Service 实现（许沣睿）
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(rollbackFor = Exception.class)
public class LectureServiceImpl implements LectureService {

    private final LectureMapper lectureMapper;

    @Override
    public LectureVO createLecture(LectureCreateRequest request) {
        Lecture content = Lecture.builder()
                .courseId(request.getCourseId())
                .title(request.getTitle())
                .status(LectureStatus.GENERATING.name())
                .currentPage(0)
                .progressMs(0L)
                .build();

        lectureMapper.insert(content);
        log.info("创建讲课内容成功: courseId={}", request.getCourseId());

        return convertToVO(content);
    }

    @Override
    public LectureVO getByCourseId(String courseId) {
        Lecture entity = lectureMapper.selectByCourseId(courseId);
        if (entity == null) {
            throw new RuntimeException("讲课内容不存在: courseId=" + courseId);
        }
        return convertToVO(entity);
    }

    @Override
    public List<LectureVO> listAll(int page, int pageSize) {
        Page<Lecture> pageParam = new Page<>(page, pageSize);
        Page<Lecture> result = lectureMapper.selectPage(pageParam, null);
        return result.getRecords().stream().map(this::convertToVO).collect(Collectors.toList());
    }

    @Override
    public void deleteByCourseId(String courseId) {
        Lecture entity = lectureMapper.selectByCourseId(courseId);
        if (entity == null) {
            throw new RuntimeException("讲课内容不存在: courseId=" + courseId);
        }
        lectureMapper.deleteById(entity.getId());
        log.info("删除讲课内容成功: courseId={}", courseId);
    }

    @Override
    public void updateGeneratedContent(String courseId, String pptJsonPath, String generatedContent,
                                       Integer totalPages, Long totalDurationMs) {
        int rows = lectureMapper.updateGeneratedContent(courseId, pptJsonPath, generatedContent,
                totalPages, totalDurationMs);
        if (rows == 0) {
            throw new RuntimeException("讲课内容不存在，无法更新生成内容: courseId=" + courseId);
        }
        log.info("更新生成内容: courseId={}, totalPages={}, totalDurationMs={}",
                courseId, totalPages, totalDurationMs);
    }

    private LectureVO convertToVO(Lecture entity) {
        return LectureVO.builder()
                .id(entity.getId())
                .courseId(entity.getCourseId())
                .title(entity.getTitle())
                .status(entity.getStatus())
                .sourceFilePath(entity.getSourceFilePath())
                .sourceFileName(entity.getSourceFileName())
                .fileSize(entity.getFileSize())
                .fileType(entity.getFileType())
                .generatedContent(entity.getGeneratedContent())
                .currentPage(entity.getCurrentPage())
                .totalPages(entity.getTotalPages())
                .progressMs(entity.getProgressMs())
                .totalDurationMs(entity.getTotalDurationMs())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
