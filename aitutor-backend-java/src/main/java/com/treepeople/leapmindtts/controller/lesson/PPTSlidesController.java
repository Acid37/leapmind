package com.treepeople.leapmindtts.controller.lesson;

import com.treepeople.leapmindtts.pojo.result.ApiResponse;
import com.treepeople.leapmindtts.pojo.vo.PPTSlidesVO;
import com.treepeople.leapmindtts.service.lesson.PPTSlidesService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * @ Package：com.treepeople.leapmindtts.controller
 * @ Project：leapMind-java
 * @ Description:
 * @ Date：2025/11/11  15:51
 */
@RestController
@RequestMapping("/api/courses")
@Slf4j
@RequiredArgsConstructor
public class PPTSlidesController {

    private final PPTSlidesService pptSlidesService;

    @GetMapping("/{courseId}/slides-data")
    public ResponseEntity<ApiResponse<List<PPTSlidesVO>>> getPPTSlides(@PathVariable String courseId) {
        log.info("获取PPT数据: {}", courseId);
        try {
            List<PPTSlidesVO> pptSlidesList = pptSlidesService.getPPTSlidesList(courseId);
            return ResponseEntity.ok(ApiResponse.success(pptSlidesList, "获取PPT数据成功"));
        }catch (Exception e){
            log.error("获取PPT数据失败: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(400, e.getMessage()));
        }
    }

}
