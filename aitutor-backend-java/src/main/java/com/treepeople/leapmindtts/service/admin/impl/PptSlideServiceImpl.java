package com.treepeople.leapmindtts.service.admin.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.treepeople.leapmindtts.mapper.PptSlideMapper;
import com.treepeople.leapmindtts.pojo.entity.PptSlide;
import com.treepeople.leapmindtts.service.admin.PptSlideService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * PPT幻灯片服务实现类
 */
@Slf4j
@Service
public class PptSlideServiceImpl extends ServiceImpl<PptSlideMapper, PptSlide> implements PptSlideService {

    @Override
    public List<PptSlide> getByCourseId(String courseId) {
        log.info("查询项目ID为 {} 的幻灯片列表", courseId);
        return baseMapper.selectByCourseId(courseId);
    }

    @Override
    public boolean createSlide(PptSlide pptSlide) {
        log.info("创建幻灯片: {}", pptSlide);
        return save(pptSlide);
    }

    @Override
    public boolean updateSlide(PptSlide pptSlide) {
        log.info("更新幻灯片: {}", pptSlide);
        return updateById(pptSlide);
    }

    @Override
    public boolean deleteSlide(Integer id) {
        log.info("删除幻灯片ID: {}", id);
        return removeById(id);
    }

    @Override
    public boolean existsByCourseId(String courseId) {
        log.info("根据项目ID查询幻灯片是否存在: {}", courseId);
        QueryWrapper<PptSlide> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("course_id", courseId);
        return count(queryWrapper) > 0;
    }
}
