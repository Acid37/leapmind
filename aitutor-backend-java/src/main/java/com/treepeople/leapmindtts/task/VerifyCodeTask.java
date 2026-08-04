package com.treepeople.leapmindtts.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.treepeople.leapmindtts.mapper.SmsVerificationCodeMapper;
import com.treepeople.leapmindtts.pojo.entity.SmsVerificationCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 *  
 * @ Package：com.treepeople.leapmindtts.task
 * @ Project：leapmind-tts - 语音分段
 * @ Description: 验证码定时任务处理类
 * @ Date：2025/11/5  17:33
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class VerifyCodeTask {
    
    private final SmsVerificationCodeMapper smsVerificationCodeMapper;

    /**
     * 处理过期验证码
     * 每5分钟执行一次，清理过期的验证码记录
     */
    @Scheduled(fixedRate = 5 * 60 * 1000) // 每5分钟执行一次
    @Transactional
    public void processExpiredVerifyCode() {
        try {
            log.debug("开始处理过期验证码任务...");
            
            LocalDateTime now = LocalDateTime.now();
            
            // 查询所有过期且未使用的验证码
            LambdaQueryWrapper<SmsVerificationCode> queryWrapper = new LambdaQueryWrapper<>();
            queryWrapper.lt(SmsVerificationCode::getExpireTime, now)
                       .eq(SmsVerificationCode::getIsUsed, 0); // 未使用的验证码
            
            List<SmsVerificationCode> expiredCodes = smsVerificationCodeMapper.selectList(queryWrapper);
            
            if (expiredCodes.isEmpty()) {
                log.debug("没有找到过期的验证码记录");
                return;
            }
            
            log.info("找到 {} 条过期验证码记录，开始处理...", expiredCodes.size());
            
            int deletedCount = deleteExpiredCodes(now);
            
            log.info("过期验证码处理完成，共处理 {} 条记录", deletedCount);
            
        } catch (Exception e) {
            log.error("处理过期验证码任务失败: {}", e.getMessage(), e);
        }
    }
    

    
    /**
     * 删除过期的验证码记录
     * 
     * @param now 当前时间
     * @return 删除的记录数
     */
    private int deleteExpiredCodes(LocalDateTime now) {
        try {
            // 批量删除过期记录
            LambdaQueryWrapper<SmsVerificationCode> deleteWrapper = new LambdaQueryWrapper<>();
            deleteWrapper.lt(SmsVerificationCode::getExpireTime, now)
                        .eq(SmsVerificationCode::getIsUsed, 0);
            
            int deletedCount = smsVerificationCodeMapper.delete(deleteWrapper);
            
            if (deletedCount > 0) {
                log.info("成功删除 {} 条过期验证码记录", deletedCount);
            }
            
            return deletedCount;
            
        } catch (Exception e) {
            log.error("删除过期验证码记录失败: {}", e.getMessage(), e);
            throw e;
        }
    }
    
    /**
     * 清理历史验证码记录
     * 每天凌晨2点执行，清理30天前的所有验证码记录
     */
    //@Scheduled(cron = "0 0 2 * * ?") // 每天凌晨2点执行
    @Transactional
    public void cleanHistoryVerifyCode() {
        try {
            log.info("开始清理历史验证码记录...");
            
            LocalDateTime thirtyDaysAgo = LocalDateTime.now().minusDays(30);
            
            // 删除30天前的所有验证码记录
            LambdaQueryWrapper<SmsVerificationCode> queryWrapper = new LambdaQueryWrapper<>();
            queryWrapper.lt(SmsVerificationCode::getExpireTime, thirtyDaysAgo);
            
            int deletedCount = smsVerificationCodeMapper.delete(queryWrapper);
            log.info("历史验证码清理完成，共删除 {} 条记录", deletedCount);
            
        } catch (Exception e) {
            log.error("清理历史验证码记录失败: {}", e.getMessage(), e);
        }
    }
    
    
}
