package com.treepeople.leapmindtts.service.lesson;

import com.treepeople.leapmindtts.pojo.dto.LectureCreateRequest;
import com.treepeople.leapmindtts.pojo.vo.LecturePageVO;
import com.treepeople.leapmindtts.pojo.vo.LectureVO;

/**
 * M4 讲课内容 Service 接口（许沣睿）
 */
public interface LectureService {

    LectureVO createLecture(LectureCreateRequest request);

    LectureVO getByCourseId(String courseId);

    LecturePageVO listAll(int page, int pageSize);

    void deleteByCourseId(String courseId);

    void updateGeneratedContent(String courseId, String pptJsonPath, String generatedContent,
                                Integer totalPages, Long totalDurationMs);
}
