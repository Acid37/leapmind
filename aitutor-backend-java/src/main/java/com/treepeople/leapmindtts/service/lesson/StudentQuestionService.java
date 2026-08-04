package com.treepeople.leapmindtts.service.lesson;

import com.baomidou.mybatisplus.extension.service.IService;
import com.treepeople.leapmindtts.pojo.entity.StudentQuestion;

import java.util.List;

/**
 * 学生提问记录服务接口
 */
public interface StudentQuestionService extends IService<StudentQuestion> {

    /**
     * 根据会话ID查询所有提问记录
     */
    List<StudentQuestion> getByCourseId(String courseId);

    /**
     * 根据会话ID和片段索引查询提问记录
     */
    List<StudentQuestion> getBySessionAndSegment(String courseId, Integer segmentIndex);

    /**
     * 根据提问类型查询记录
     */
    List<StudentQuestion> getByQuestionType(String courseId, String questionType);

    /**
     * 保存文本提问记录
     */
    boolean saveTextQuestion(String courseId, Integer segmentIndex, String questionText, String answerText);

    /**
     * 保存语音提问记录
     */
    boolean saveVoiceQuestion(String courseId, Integer segmentIndex, String questionText,
                            String answerText, byte[] questionAudio, byte[] answerAudio);

    /**
     * 删除指定会话的所有提问记录
     */
    boolean deleteByCourseId(String courseId);

    /**
     * 查询指定会话的提问总数
     */
    int countByCourseId(String courseId);

    /**
     * 根据时间范围查询提问记录
     */
    List<StudentQuestion> getByTimeRange(String courseId, String startTime, String endTime);

    /**
     * 查询最近的提问记录
     */
    List<StudentQuestion> getRecentQuestions(String courseId, Integer limit);

    /**
     * 更新提问的回答
     */
    boolean updateAnswer(Long questionId, String answerText, byte[] answerAudio);

    /**
     * 查询所有全局打断的提问记录（courseId为null的记录）
     */
    List<StudentQuestion> getGlobalInterruptions();

    /**
     * 根据时间范围查询全局打断记录
     */
    List<StudentQuestion> getGlobalInterruptionsByTimeRange(String startTime, String endTime);

    /**
     * 查询最近的全局打断记录
     */
    List<StudentQuestion> getRecentGlobalInterruptions(Integer limit);

    /**
     * 根据问题文本模糊查询
     */
    List<StudentQuestion> searchByQuestionText(String keyword);

    /**
     * 根据回答文本模糊查询
     */
    List<StudentQuestion> searchByAnswerText(String keyword);
}
