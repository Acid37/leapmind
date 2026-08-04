package com.treepeople.leapmindtts.service.lesson.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.treepeople.leapmindtts.mapper.ProjectOutlineMapper;
import com.treepeople.leapmindtts.pojo.dto.ProjectOutlineCreateRequest;
import com.treepeople.leapmindtts.pojo.dto.ProjectOutlineUpdateRequest;
import com.treepeople.leapmindtts.pojo.entity.ProjectOutline;
import com.treepeople.leapmindtts.pojo.vo.ProjectOutlineVO;
import com.treepeople.leapmindtts.service.admin.ProjectOutlineService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 项目大纲服务实现类
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProjectOutlineServiceImpl implements ProjectOutlineService {

    private final ProjectOutlineMapper projectOutlineMapper;

    @Override
    public List<ProjectOutlineVO> getAllOutlines() {
        log.info("查询所有项目大纲");

        LambdaQueryWrapper<ProjectOutline> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.orderByDesc(ProjectOutline::getCreateTime);

        List<ProjectOutline> outlines = projectOutlineMapper.selectList(queryWrapper);

        return outlines.stream()
                      .map(this::convertToVO)
                      .collect(Collectors.toList());
    }

    @Override
    public ProjectOutlineVO createOutline(ProjectOutlineCreateRequest request) {
        log.info("创建项目大纲，项目ID: {}", request.getCourseId());

        ProjectOutline outline = ProjectOutline.builder()
                .courseId(request.getCourseId())
                .outlineJson(request.getOutlineJson())
                .createTime(LocalDateTime.now())
                .updateTime(LocalDateTime.now())
                .build();

        int result = projectOutlineMapper.insert(outline);
        if (result > 0) {
            log.info("项目大纲创建成功，ID: {}", outline.getId());
            return convertToVO(outline);
        } else {
            log.error("项目大纲创建失败");
            throw new RuntimeException("创建项目大纲失败");
        }
    }

    @Override
    public ProjectOutlineVO getOutlineById(String courseId) {
        log.info("查询项目大纲，ID: {}", courseId);
        QueryWrapper<ProjectOutline> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("course_id", courseId);

        ProjectOutline outline = projectOutlineMapper.selectById(courseId);
        if (outline == null) {
            log.warn("项目大纲不存在，ID: {}", courseId);
            throw new RuntimeException("项目大纲不存在");
        }

        return convertToVO(outline);
    }

    @Override
    public List<ProjectOutlineVO> getOutlinesByCourseId(String courseId) {
        log.info("根据项目ID查询大纲列表，项目ID: {}", courseId);

        LambdaQueryWrapper<ProjectOutline> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(ProjectOutline::getCourseId, courseId)
                   .orderByDesc(ProjectOutline::getCreateTime);

        List<ProjectOutline> outlines = projectOutlineMapper.selectList(queryWrapper);

        return outlines.stream()
                      .map(this::convertToVO)
                      .collect(Collectors.toList());
    }

    @Override
    public ProjectOutlineVO updateOutline(ProjectOutlineUpdateRequest request) {
        log.info("更新项目大纲，ID: {}", request.getId());

        ProjectOutline existingOutline = projectOutlineMapper.selectById(request.getId());
        if (existingOutline == null) {
            log.warn("项目大纲不存在，ID: {}", request.getId());
            throw new RuntimeException("项目大纲不存在");
        }

        existingOutline.setOutlineJson(request.getOutlineJson());
        existingOutline.setUpdateTime(LocalDateTime.now());

        int result = projectOutlineMapper.updateById(existingOutline);
        if (result > 0) {
            log.info("项目大纲更新成功，ID: {}", request.getId());
            return convertToVO(existingOutline);
        } else {
            log.error("项目大纲更新失败，ID: {}", request.getId());
            throw new RuntimeException("更新项目大纲失败");
        }
    }

    @Override
    public boolean deleteOutline(Integer id) {
        log.info("删除项目大纲，ID: {}", id);

        ProjectOutline existingOutline = projectOutlineMapper.selectById(id);
        if (existingOutline == null) {
            log.warn("项目大纲不存在，ID: {}", id);
            throw new RuntimeException("项目大纲不存在");
        }

        int result = projectOutlineMapper.deleteById(id);
        if (result > 0) {
            log.info("项目大纲删除成功，ID: {}", id);
            return true;
        } else {
            log.error("项目大纲删除失败，ID: {}", id);
            return false;
        }
    }

    /**
     * 实体转换为VO
     */
    private ProjectOutlineVO convertToVO(ProjectOutline outline) {
        ProjectOutlineVO vo = new ProjectOutlineVO();
        BeanUtils.copyProperties(outline, vo);
        return vo;
    }
}
