package com.treepeople.leapmindtts.service.lesson;

import com.treepeople.leapmindtts.pojo.dto.LectureProgressDTO;

/**
 * M4 讲课进度 Service 接口（许沣睿）
 */
public interface LectureProgressService {

    void saveProgress(LectureProgressDTO progressDTO);

    LectureProgressDTO getProgress(String courseId);

    void savePlaybackSnapshot(String courseId, String snapshotJson);

    String getPlaybackSnapshot(String courseId);
}
