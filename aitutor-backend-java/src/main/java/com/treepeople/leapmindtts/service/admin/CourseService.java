package com.treepeople.leapmindtts.service.admin;

import com.baomidou.mybatisplus.extension.service.IService;
import com.treepeople.leapmindtts.pojo.dto.AdminCourseSectionDTO;
import com.treepeople.leapmindtts.pojo.dto.CourseCreateRequest;
import com.treepeople.leapmindtts.pojo.dto.CourseSectionDTO;
import com.treepeople.leapmindtts.pojo.dto.CourseUpdateRequest;
import com.treepeople.leapmindtts.pojo.entity.CourseSection;
import com.treepeople.leapmindtts.pojo.vo.CourseVO;

import java.util.List;

/**
 * @ Package：com.treepeople.leapmindtts.service
 * @ Project：leapmind-tts - 语音分段
 * @ Description:
 * @ Date：2025/11/2  20:55
 */
public interface CourseService extends IService<CourseSection> {
    List<CourseVO> getCourseSection(CourseSectionDTO courseSectionDTO);

    /**
     * 创建课程
     * @param request 创建请求
     * @return 课程视图对象
     */
    CourseVO createCourse(CourseCreateRequest request);

    /**
     * 根据ID获取课程详情
     * @param courseId 课程ID
     * @return 课程视图对象
     */
    CourseVO getCourseById(String courseId);

    /**
     * 更新课程信息
     * @param id 课程ID
     * @param request 更新请求
     * @return 更新后的课程视图对象
     */
    CourseVO updateCourse(Integer id, CourseUpdateRequest request);

    /**
     * 删除课程
     * @param id 课程ID
     */
    void deleteCourse(Integer id);

    /**
     * 根据阶段查询课程列表
     * @param stageName 阶段名称
     * @return 课程列表
     */
    List<CourseVO> getCoursesByStage(String stageName);

    /**
     * 根据学科查询课程列表
     * @param subject 学科名称
     * @return 课程列表
     */
    List<CourseVO> getCoursesBySubject(String subject);

    /**
     * 获取所有课程列表
     * @return 课程列表
     */
    List<CourseVO> getAllCourses();

    /**
     * 根据阶段、年级、学期、学科查询课程列表
     * @param adminCourseSectionDTO 查询条件
     * @return 课程列表
     */
    List<CourseVO> getCourseSectionByStageGradeSemesterSubject(AdminCourseSectionDTO adminCourseSectionDTO);
}

