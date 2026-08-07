package com.treepeople.leapmindtts.task;

import com.treepeople.leapmindtts.config.PythonServiceProperties;
import com.treepeople.leapmindtts.mapper.ReviewReminderMapper;
import com.treepeople.leapmindtts.pojo.entity.ReviewReminder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.LocalDate;
import java.util.List;

/**
 * 复习计算定时任务
 * <p>
 * 负责每日凌晨自动触发复习计划计算、模块事件同步和过期数据清理。
 * 三个定时任务按时间顺序依次执行，互不依赖（独立 try-catch 保证单个任务失败不影响其他）：
 * <p>
 * <b>任务调度时间表：</b>
 * <table border="1">
 *   <tr><th>时间</th><th>任务</th><th>说明</th></tr>
 *   <tr><td>02:00</td><td>全量复习计算</td><td>调用 Python AI 服务，基于遗忘曲线算法为所有用户生成复习提醒</td></tr>
 *   <tr><td>03:00</td><td>模块事件同步</td><td>将 M1/M2/M4/M7 各模块上报的学习事件汇总发送给 Python 服务处理</td></tr>
 *   <tr><td>04:00</td><td>过期提醒清理</td><td>扫描超过 30 天未复习的记录，记录日志供后续分析</td></tr>
 * </table>
 * <p>
 * <b>容错设计：</b>
 * <ul>
 *   <li>每个任务独立 try-catch，单个失败不影响后续任务执行</li>
 *   <li>调用 Python 服务失败仅记录日志，不中断 Spring 调度线程</li>
 *   <li>Python 服务不可用时，复习提醒不会更新，但已存在的提醒仍可正常查询</li>
 * </ul>
 * <p>
 * <b>依赖：</b>
 * <ul>
 *   <li>{@link WebClient} — HTTP 客户端，调用 Python 服务</li>
 *   <li>{@link PythonServiceProperties} — Python 服务地址和接口路径配置</li>
 *   <li>{@link ReviewReminderMapper} — 复习提醒数据查询</li>
 * </ul>
 *
 * @author wuminxi
 * @date 2026-07-21
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class ReviewCalculationTask {

    private final WebClient webClient;
    private final PythonServiceProperties pythonServiceProperties;
    private final ReviewReminderMapper reviewReminderMapper;

    /**
     * 全量复习计算
     * <p>
     * 每天凌晨 2:00 执行，调用 Python AI 服务 {@code /api/review/calculate-all} 接口。
     * Python 服务负责：
     * <ul>
     *   <li>读取 event_collections 表中各模块上报的学习行为数据</li>
     *   <li>基于艾宾浩斯遗忘曲线 + 间隔重复算法计算每个用户的复习节点</li>
     *   <li>将计算结果回写到 review_reminders 表</li>
     * </ul>
     * <p>
     * Cron 表达式：{@code 0 0 2 * * ?} — 秒 分 时 日 月 周，即每天 02:00:00
     */
    @Scheduled(cron = "0 0 2 * * ?")
    public void executeFullReviewCalculation() {
        log.info("========== 全量复习计算定时任务开始 ==========");

        try {
            // 拼接 Python 服务全量复习计算接口 URL
            String pythonUrl = pythonServiceProperties.getBaseUrl() + pythonServiceProperties.getReviewCalculationPath();
            log.info("调用 Python 服务全量复习计算接口: {}", pythonUrl);

            // 同步阻塞调用 Python 服务（定时任务线程允许阻塞等待）
            String response = webClient.post()
                    .uri(pythonUrl)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            log.info("Python 服务全量复习计算响应: {}", response);
            log.info("========== 全量复习计算定时任务完成 ==========");

        } catch (Exception e) {
            // 调用失败仅记录日志，不向上抛出，避免影响其他定时任务
            log.error("调用 Python 服务全量复习计算失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 模块事件同步
     * <p>
     * 每天凌晨 3:00 执行，调用 Python AI 服务 {@code /api/events/process} 接口。
     * 将 M1/M2/M4/M7 模块当天上报的学习事件批量发送给 Python 服务进行：
     * <ul>
     *   <li>事件数据清洗和去重</li>
     *   <li>用户学习行为特征提取</li>
     *   <li>知识掌握度评估更新</li>
     * </ul>
     * <p>
     * Cron 表达式：{@code 0 0 3 * * ?} — 每天 03:00:00
     */
    @Scheduled(cron = "0 0 3 * * ?")
    public void syncModuleEvents() {
        log.info("========== 模块事件同步定时任务开始 ==========");

        try {
            // 拼接 Python 服务事件处理接口 URL
            String pythonUrl = pythonServiceProperties.getBaseUrl() + pythonServiceProperties.getEventProcessPath();
            log.info("调用 Python 服务事件处理接口: {}", pythonUrl);

            // 同步阻塞调用，等待 Python 服务处理完成
            String response = webClient.post()
                    .uri(pythonUrl)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            log.info("Python 服务事件处理响应: {}", response);
            log.info("========== 模块事件同步定时任务完成 ==========");

        } catch (Exception e) {
            log.error("调用 Python 服务事件处理失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 过期复习提醒清理
     * <p>
     * 每天凌晨 4:00 执行，扫描所有待复习记录，识别超过 30 天仍未复习的过期记录。
     * <p>
     * 当前实现为「检测 + 日志记录」模式，不做实际删除，原因：
     * <ul>
     *   <li>过期数据具有分析价值（用户学习习惯、遗忘曲线校准）</li>
     *   <li>避免误删导致数据不可恢复</li>
     *   <li>后续可扩展为标记过期状态而非物理删除</li>
     * </ul>
     * <p>
     * Cron 表达式：{@code 0 0 4 * * ?} — 每天 04:00:00
     */
    @Scheduled(cron = "0 0 4 * * ?")
    public void cleanExpiredReminders() {
        log.info("========== 过期复习提醒清理任务开始 ==========");

        try {
            // 查询所有到期未复习的记录
            List<ReviewReminder> pendingReminders = reviewReminderMapper.selectAllPendingReminders(LocalDate.now());
            int expiredCount = 0;

            // 遍历识别超过 30 天仍未复习的记录
            for (ReviewReminder reminder : pendingReminders) {
                if (reminder.getScheduledDate() != null
                        && reminder.getScheduledDate().isBefore(LocalDate.now().minusDays(30))) {
                    log.debug("过期复习提醒: id={}, userId={}, courseId={}, scheduledDate={}",
                            reminder.getId(), reminder.getUserId(),
                            reminder.getCourseId(), reminder.getScheduledDate());
                    expiredCount++;
                }
            }

            log.info("过期复习提醒清理完成，共发现 {} 条超期（>30天）未复习记录", expiredCount);
            log.info("========== 过期复习提醒清理任务完成 ==========");

        } catch (Exception e) {
            log.error("过期复习提醒清理任务失败: {}", e.getMessage(), e);
        }
    }
}
