package com.treepeople.leapmindtts.service.admin.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.treepeople.leapmindtts.mapper.CourseMapper;
import com.treepeople.leapmindtts.pojo.dto.AdminCourseSectionDTO;
import com.treepeople.leapmindtts.pojo.dto.CourseCreateRequest;
import com.treepeople.leapmindtts.pojo.dto.CourseSectionDTO;
import com.treepeople.leapmindtts.pojo.dto.CourseUpdateRequest;
import com.treepeople.leapmindtts.pojo.entity.CourseSection;
import com.treepeople.leapmindtts.pojo.vo.CourseVO;
import com.treepeople.leapmindtts.service.admin.CourseService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 *
 * @ Package：com.treepeople.leapmindtts.service.impl
 * @ Project：leapmind-tts - 语音分段
 * @ Description:
 * @ Date：2025/11/2  20:57
 */
@Service
public class CourseServiceImpl extends ServiceImpl<CourseMapper, CourseSection> implements CourseService {
    @Override
    public List<CourseVO> getCourseSection(CourseSectionDTO courseSectionDTO) {

        QueryWrapper<CourseSection> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("stage_name", courseSectionDTO.getStageName())
            .eq("grade_name", courseSectionDTO.getGradeName())
            .eq("semester", courseSectionDTO.getSemester().getChineseName())
            .eq("subject", courseSectionDTO.getSubject())
            .eq("chapter_number", courseSectionDTO.getChapterNumber());

        return baseMapper.selectList(queryWrapper).stream()
            .map(this::convertToCourseVO)
            .toList();
    }

    @Override
    public CourseVO createCourse(CourseCreateRequest request) {
        QueryWrapper<CourseSection> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("subject", request.getSubject())
            .eq("stage_name", request.getStageName())
            .eq("grade_name", request.getGradeName())
            .eq("semester", request.getSemester().getChineseName())
            .eq("chapter_number", request.getChapterNumber())
            .eq("section_number", request.getSectionNumber());
        CourseSection course = baseMapper.selectOne(queryWrapper);
        if (course != null) {
            throw new RuntimeException("课程已存在");
        }
        CourseSection courseSection = new CourseSection();
        String courseId = "ppt_session_" + UUID.randomUUID().toString().replace("-", "");
        courseSection.setCourseId(courseId);
        courseSection.setSubject(request.getSubject());
        courseSection.setStageName(request.getStageName());
        courseSection.setGradeName(request.getGradeName());
        courseSection.setSemester(request.getSemester().getChineseName());
        courseSection.setChapterNumber(request.getChapterNumber());
        courseSection.setChapterTitle(request.getChapterTitle());
        courseSection.setSectionContent(request.getSectionContent());
        courseSection.setSectionNumber(request.getSectionNumber());
        courseSection.setSectionTitle(request.getSectionTitle());

        baseMapper.insert(courseSection);
        return convertToCourseVO(courseSection);
    }

    @Override
    public CourseVO getCourseById(String courseId) {
        QueryWrapper<CourseSection> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("course_id", courseId);
        CourseSection course = baseMapper.selectOne(queryWrapper);
        if (course == null) {
            throw new RuntimeException("课程不存在");
        }
        return convertToCourseVO(course);
    }

    @Override
    public CourseVO updateCourse(Integer id, CourseUpdateRequest request) {
        CourseSection courseSection = baseMapper.selectById(id);
        if (courseSection == null) {
            throw new RuntimeException("课程不存在");
        }

        if (request.getSubject() != null) {
            courseSection.setSubject(request.getSubject());
        }
        if (request.getStageName() != null) {
            courseSection.setStageName(request.getStageName());
        }
        if (request.getGradeName() != null) {
            courseSection.setGradeName(request.getGradeName());
        }
        if (request.getSemester() != null) {
            courseSection.setSemester(request.getSemester().getChineseName());
        }
        if (request.getChapterNumber() != null) {
            courseSection.setChapterNumber(request.getChapterNumber());
        }
        if (request.getChapterTitle() != null) {
            courseSection.setChapterTitle(request.getChapterTitle());
        }
        if (request.getSectionContent() != null) {
            courseSection.setSectionContent(request.getSectionContent());
        }
        if (request.getSectionNumber() != null) {
            courseSection.setSectionNumber(request.getSectionNumber());
        }
        if (request.getSectionTitle() != null) {
            courseSection.setSectionTitle(request.getSectionTitle());
        }

        baseMapper.updateById(courseSection);
        return convertToCourseVO(courseSection);
    }

    @Override
    public void deleteCourse(Integer id) {
        CourseSection courseSection = baseMapper.selectById(id);
        if (courseSection == null) {
            throw new RuntimeException("课程不存在");
        }
        baseMapper.deleteById(id);
    }

    @Override
    public List<CourseVO> getCoursesByStage(String stageName) {
        QueryWrapper<CourseSection> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("stage_name", stageName);
        return baseMapper.selectList(queryWrapper).stream()
            .map(this::convertToCourseVO)
            .toList();
    }

    @Override
    public List<CourseVO> getCoursesBySubject(String subject) {
        QueryWrapper<CourseSection> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("subject", subject);
        return baseMapper.selectList(queryWrapper).stream()
            .map(this::convertToCourseVO)
            .toList();
    }

    @Override
    public List<CourseVO> getAllCourses() {
        return baseMapper.selectList(null).stream()
            .map(this::convertToCourseVO)
            .toList();
    }

    @Override
    public List<CourseVO> getCourseSectionByStageGradeSemesterSubject(AdminCourseSectionDTO adminCourseSectionDTO) {
        QueryWrapper<CourseSection> queryWrapper = new QueryWrapper<>();
        queryWrapper
            .eq(adminCourseSectionDTO.getStageName() != null, "stage_name", adminCourseSectionDTO.getStageName())
            .eq(adminCourseSectionDTO.getGradeName() != null, "grade_name", adminCourseSectionDTO.getGradeName())
            .eq(adminCourseSectionDTO.getSemester() != null, "semester", adminCourseSectionDTO.getSemester().getChineseName())
            .eq(adminCourseSectionDTO.getSubject() != null, "subject", adminCourseSectionDTO.getSubject())
            .eq(adminCourseSectionDTO.getChapterNumber() != null, "chapter_number", adminCourseSectionDTO.getChapterNumber());
        return baseMapper.selectList(queryWrapper).stream()
            .map(this::convertToCourseVO)
            .toList();

    }

    /**
     * 将CourseSection转换为CourseVO
     */
    private CourseVO convertToCourseVO(CourseSection courseSection) {
        return CourseVO.builder()
            .id(courseSection.getId())
            .courseId(courseSection.getCourseId())
            .subject(courseSection.getSubject())
            .stageName(courseSection.getStageName())
            .gradeName(courseSection.getGradeName())
            .semester(courseSection.getSemester())
            .chapterNumber(courseSection.getChapterNumber())
            .chapterTitle(courseSection.getChapterTitle())
            .sectionContent(courseSection.getSectionContent())
            .sectionNumber(courseSection.getSectionNumber())
            .sectionTitle(courseSection.getSectionTitle())
            .build();
    }
}
