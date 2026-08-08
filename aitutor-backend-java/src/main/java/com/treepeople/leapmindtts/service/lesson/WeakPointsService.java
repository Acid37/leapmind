package com.treepeople.leapmindtts.service.lesson;

import com.treepeople.leapmindtts.pojo.dto.ExerciseRecordRequest;
import com.treepeople.leapmindtts.pojo.vo.ExerciseVO;
import com.treepeople.leapmindtts.pojo.vo.KnowledgeGraphVO;
import com.treepeople.leapmindtts.pojo.vo.RecommendQuestionVO;
import com.treepeople.leapmindtts.pojo.vo.UserWeakPointVO;
import com.treepeople.leapmindtts.pojo.vo.WeakPointsAnalysisVO;

import java.util.List;

/**
 * 薄弱点分析服务接口
 */
public interface WeakPointsService {

    /**
     * 查询用户薄弱点列表
     */
    List<UserWeakPointVO> getUserWeakPoints(Long userId, String subject, String status);

    /**
     * 获取/触发 AI 综合分析
     */
    WeakPointsAnalysisVO getOrCreateAnalysis(Long userId);

    /**
     * 推荐练习题（含去重和优先级逻辑）
     */
    List<ExerciseVO> recommendExercises(Long userId, String subject, String knowledgePoint, Integer count);

    /**
     * 记录练习结果
     */
    void recordExerciseResult(ExerciseRecordRequest request);

    /**
     * 根据知识点推荐具体题目（薄弱点详情页）
     *
     * @param userId         用户ID
     * @param knowledgePoint 知识点
     * @param count          推荐数量
     * @return 推荐题目列表
     */
    List<RecommendQuestionVO> recommendQuestions(Long userId, String knowledgePoint, Integer count);

    /**
     * 获取用户知识图谱（知识图谱页）
     *
     * @param userId  用户ID
     * @param subject 学科（可选，为空则返回所有学科）
     * @return 知识图谱（节点+边）
     */
    KnowledgeGraphVO getKnowledgeGraph(Long userId, String subject);
}
