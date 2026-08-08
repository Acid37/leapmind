package com.treepeople.leapmindtts.service.lesson;

import com.treepeople.leapmindtts.pojo.vo.WeakPointsImprovementVO;

/**
 * 薄弱点改善追踪服务接口（M3 薄弱点模块）
 */
public interface WeakPointsImprovementService {

    /**
     * 查询用户薄弱点改善报告
     * <p>
     * 基于 {@code user_exercises} 练习记录，对比本期与上期正确率，
     * 结合 {@code user_weak_points} 状态（HIGH/RESOLVED 等）生成改善报告。
     *
     * @param userId 用户ID
     * @param period 统计周期：week/month，默认 month
     * @return 改善报告
     */
    WeakPointsImprovementVO getImprovementReport(Long userId, String period);
}
