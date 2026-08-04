package com.treepeople.leapmindtts.service.lesson.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.treepeople.leapmindtts.mapper.PPTSlidesMapper;
import com.treepeople.leapmindtts.pojo.entity.PPTSlides;
import com.treepeople.leapmindtts.pojo.vo.PPTSlidesVO;
import com.treepeople.leapmindtts.service.lesson.PPTSlidesService;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 *
 * @ Package：com.treepeople.leapmindtts.service.impl
 * @ Project：leapMind-java
 * @ Description:
 * @ Date：2025/11/11  15:53
 */
@Service
public class PPTSlidesServiceImpl extends ServiceImpl<PPTSlidesMapper, PPTSlides>implements PPTSlidesService {
    @Override
    public List<PPTSlidesVO> getPPTSlidesList(String courseId) {
        QueryWrapper<PPTSlides> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("course_id", courseId);
        List<PPTSlides> pptSlidesList = this.list(queryWrapper);
        List<PPTSlidesVO> pptSlidesVOList = pptSlidesList.stream().map(pptSlides -> new PPTSlidesVO(pptSlides.getCourseId(), pptSlides.getTitle(), pptSlides.getHtmlContent())).toList();
        if (pptSlidesVOList.isEmpty()){
            return null;
        }
        return pptSlidesVOList;
    }
}
