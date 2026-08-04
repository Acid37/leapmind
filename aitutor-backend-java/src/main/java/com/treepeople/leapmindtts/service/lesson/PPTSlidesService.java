package com.treepeople.leapmindtts.service.lesson;

import com.baomidou.mybatisplus.extension.service.IService;
import com.treepeople.leapmindtts.pojo.entity.PPTSlides;
import com.treepeople.leapmindtts.pojo.vo.PPTSlidesVO;

import java.util.List;

/**
 *
 * @ Package：com.treepeople.leapmindtts.service
 * @ Project：leapMind-java
 * @ Description:
 * @ Date：2025/11/11  15:53
 */
public interface PPTSlidesService extends IService<PPTSlides> {

    List<PPTSlidesVO> getPPTSlidesList(String courseId);
}
