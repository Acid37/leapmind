package com.treepeople.leapmindtts.service.admin;

import com.baomidou.mybatisplus.extension.service.IService;
import com.treepeople.leapmindtts.pojo.entity.PptSlide;

import java.util.List;

/**
 * PPT幻灯片服务接口
 */
public interface PptSlideService extends IService<PptSlide> {

    /**
     * 根据项目ID查询幻灯片列表
     *
     * @param courseId 项目ID
     * @return 幻灯片列表
     */
    List<PptSlide> getByCourseId(String courseId);

    /**
     * 创建幻灯片
     *
     * @param pptSlide 幻灯片信息
     * @return 创建结果
     */
    boolean createSlide(PptSlide pptSlide);

    /**
     * 更新幻灯片
     *
     * @param pptSlide 幻灯片信息
     * @return 更新结果
     */
    boolean updateSlide(PptSlide pptSlide);

    /**
     * 删除幻灯片
     *
     * @param id 幻灯片ID
     * @return 删除结果
     */
    boolean deleteSlide(Integer id);

    boolean existsByCourseId(String courseId);
}
