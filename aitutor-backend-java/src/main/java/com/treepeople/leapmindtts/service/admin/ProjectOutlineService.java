package com.treepeople.leapmindtts.service.admin;

import com.treepeople.leapmindtts.pojo.dto.ProjectOutlineCreateRequest;
import com.treepeople.leapmindtts.pojo.dto.ProjectOutlineUpdateRequest;
import com.treepeople.leapmindtts.pojo.vo.ProjectOutlineVO;

import java.util.List;

/**
 * 项目大纲服务接口
 */
public interface ProjectOutlineService {

    /**
     * 获取所有项目大纲
     *
     * @return 所有大纲列表
     */
    List<ProjectOutlineVO> getAllOutlines();

    /**
     * 创建项目大纲
     *
     * @param request 创建请求
     * @return 创建的大纲信息
     */
    ProjectOutlineVO createOutline(ProjectOutlineCreateRequest request);

    /**
     * 根据ID查询大纲
     *
     * @param id 大纲ID
     * @return 大纲信息
     */
    ProjectOutlineVO getOutlineById(String courseId);

    /**
     * 根据项目ID查询大纲列表
     *
     * @param courseId 项目ID
     * @return 大纲列表
     */
    List<ProjectOutlineVO> getOutlinesByCourseId(String courseId);

    /**
     * 更新项目大纲
     *
     * @param request 更新请求
     * @return 更新后的大纲信息
     */
    ProjectOutlineVO updateOutline(ProjectOutlineUpdateRequest request);

    /**
     * 删除项目大纲
     *
     * @param id 大纲ID
     * @return 是否删除成功
     */
    boolean deleteOutline(Integer id);
}
