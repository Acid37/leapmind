package com.treepeople.leapmindtts.service.lesson.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.treepeople.leapmindtts.mapper.StudentQuestionMapper;
import com.treepeople.leapmindtts.pojo.entity.StudentQuestion;
import com.treepeople.leapmindtts.service.lesson.StudentQuestionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 学生提问记录服务实现类
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class StudentQuestionServiceImpl extends ServiceImpl<StudentQuestionMapper, StudentQuestion> implements StudentQuestionService {

    private final StudentQuestionMapper studentQuestionMapper;

    @Override
    public List<StudentQuestion> getByCourseId(String courseId) {
        return studentQuestionMapper.selectByCourseId(courseId);
    }

    @Override
    public List<StudentQuestion> getBySessionAndSegment(String courseId, Integer segmentIndex) {
        return studentQuestionMapper.selectBySessionAndSegment(courseId, segmentIndex);
    }

    @Override
    public List<StudentQuestion> getByQuestionType(String courseId, String questionType) {
        return studentQuestionMapper.selectByQuestionType(courseId, questionType);
    }

    @Override
    public boolean saveTextQuestion(String courseId, Integer segmentIndex, String questionText, String answerText) {
        StudentQuestion question = StudentQuestion.builder()
                .courseId(courseId)
                .segmentIndex(segmentIndex)
                .questionText(questionText)
                .answerText(answerText)
                .questionType("TEXT")
                .createdAt(LocalDateTime.now())
                .build();

        boolean result = save(question);
        if (result) {
            log.info("保存文本提问记录成功，会话ID: {}, 片段索引: {}, 问题: {}",
                    courseId, segmentIndex, questionText);
        } else {
            log.error("保存文本提问记录失败，会话ID: {}, 片段索引: {}", courseId, segmentIndex);
        }
        return result;
    }

    @Override
    public boolean saveVoiceQuestion(String courseId, Integer segmentIndex, String questionText,
                                   String answerText, byte[] questionAudio, byte[] answerAudio) {
        StudentQuestion question = StudentQuestion.builder()
                .courseId(courseId)
                .segmentIndex(segmentIndex)
                .questionText(questionText)
                .answerText(answerText)
                .questionAudio(questionAudio)
                .answerAudio(answerAudio)
                .questionType("VOICE")
                .createdAt(LocalDateTime.now())
                .build();

        boolean result = save(question);
        if (result) {
            log.info("保存语音提问记录成功，会话ID: {}, 片段索引: {}, 问题: {}",
                    courseId, segmentIndex, questionText);
        } else {
            log.error("保存语音提问记录失败，会话ID: {}, 片段索引: {}", courseId, segmentIndex);
        }
        return result;
    }

    @Override
    public boolean deleteByCourseId(String courseId) {
        int result = studentQuestionMapper.deleteByCourseId(courseId);
        if (result > 0) {
            log.info("删除会话提问记录成功，会话ID: {}, 删除数量: {}", courseId, result);
        } else {
            log.info("没有找到要删除的提问记录，会话ID: {}", courseId);
        }
        return result >= 0; // 即使没有删除任何记录也认为是成功的
    }

    @Override
    public int countByCourseId(String courseId) {
        return studentQuestionMapper.countByCourseId(courseId);
    }

    @Override
    public List<StudentQuestion> getByTimeRange(String courseId, String startTime, String endTime) {
        return studentQuestionMapper.selectByTimeRange(courseId, startTime, endTime);
    }

    @Override
    public List<StudentQuestion> getRecentQuestions(String courseId, Integer limit) {
        return studentQuestionMapper.selectRecentQuestions(courseId, limit);
    }

    @Override
    public boolean updateAnswer(Long questionId, String answerText, byte[] answerAudio) {
        StudentQuestion question = getById(questionId);
        if (question != null) {
            question.setAnswerText(answerText);
            question.setAnswerAudio(answerAudio);

            boolean result = updateById(question);
            if (result) {
                log.info("更新提问回答成功，问题ID: {}, 会话ID: {}", questionId, question.getCourseId());
            } else {
                log.error("更新提问回答失败，问题ID: {}", questionId);
            }
            return result;
        } else {
            log.warn("要更新的提问记录不存在，问题ID: {}", questionId);
            return false;
        }
    }

    @Override
    public List<StudentQuestion> getGlobalInterruptions() {
        return studentQuestionMapper.selectGlobalInterruptions();
    }

    @Override
    public List<StudentQuestion> getGlobalInterruptionsByTimeRange(String startTime, String endTime) {
        return studentQuestionMapper.selectGlobalInterruptionsByTimeRange(startTime, endTime);
    }

    @Override
    public List<StudentQuestion> getRecentGlobalInterruptions(Integer limit) {
        return studentQuestionMapper.selectRecentGlobalInterruptions(limit);
    }

    @Override
    public List<StudentQuestion> searchByQuestionText(String keyword) {
        return studentQuestionMapper.searchByQuestionText(keyword);
    }

    @Override
    public List<StudentQuestion> searchByAnswerText(String keyword) {
        return studentQuestionMapper.searchByAnswerText(keyword);
    }
}
