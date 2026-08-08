package com.treepeople.leapmindtts.service.lesson;

import com.treepeople.leapmindtts.pojo.dto.ExerciseRecordRequest;
import com.treepeople.leapmindtts.pojo.dto.PracticePlanRequest;
import com.treepeople.leapmindtts.pojo.result.PageResult;
import com.treepeople.leapmindtts.pojo.vo.ExerciseVO;
import com.treepeople.leapmindtts.pojo.vo.KnowledgeGraphVO;
import com.treepeople.leapmindtts.pojo.vo.PracticePlanVO;
import com.treepeople.leapmindtts.pojo.vo.RecommendQuestionVO;
import com.treepeople.leapmindtts.pojo.vo.UserWeakPointVO;
import com.treepeople.leapmindtts.pojo.vo.WeakPointDetailVO;
import com.treepeople.leapmindtts.pojo.vo.WeakPointsAnalysisVO;

import java.util.List;

/**
 * 薄弱点分析服务接口
 */
public interface WeakPointsService {

    /**
     * 查询用户薄弱点列表（分页）
     *
     * @param userId  用户ID
     * @param subject 学科（可选）
     * @param status  状态（可选）：ACTIVE/RESOLVED/IMPROVING
     * @param page    页码（默认1）
     * @param size    每页数量（默认20）
     * @return 分页结果
     */
    PageResult<UserWeakPointVO> getUserWeakPoints(Long userId, String subject, String status, Integer page, Integer size);

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
     * 根据知识点名称推荐具体题目（薄弱点详情页，内部翻译后调用 Python M3 引擎）
     *
     * @param userId         用户ID
     * @param knowledgePoint 知识点名称
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

    /**
     * 获取单个薄弱点详情
     *
     * @param id 薄弱点记录ID
     * @return 薄弱点详情（含基本信息、近期错题、趋势、AI分析）
     */
    WeakPointDetailVO getWeakPointDetail(Long id);

    /**
     * 根据薄弱点生成练习计划
     *
     * @param request 用户ID + 目标知识点列表
     * @return 练习计划（题目序列 + 预计时间）
     */
    PracticePlanVO generatePracticePlan(PracticePlanRequest request);
}
