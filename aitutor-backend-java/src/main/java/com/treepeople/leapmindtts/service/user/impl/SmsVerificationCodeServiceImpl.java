package com.treepeople.leapmindtts.service.user.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.treepeople.leapmindtts.mapper.SmsVerificationCodeMapper;
import com.treepeople.leapmindtts.pojo.dto.VerifyCodeDTO;
import com.treepeople.leapmindtts.pojo.entity.SmsVerificationCode;
import com.treepeople.leapmindtts.service.user.SmsVerificationCodeService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 *
 * @ Package：com.treepeople.leapmindtts.service.impl
 * @ Project：leapmind-tts - 语音分段
 * @ Description:
 * @ Date：2025/11/4  09:45
 */
@Service
public class SmsVerificationCodeServiceImpl extends ServiceImpl<SmsVerificationCodeMapper, SmsVerificationCode> implements SmsVerificationCodeService {
    @Override
    public Boolean verifyCode(VerifyCodeDTO verifyCodeDTO) {
        QueryWrapper<SmsVerificationCode> queryWrapper = new QueryWrapper<>();
        queryWrapper
                .eq("phone", verifyCodeDTO.getPhoneNumber())
                .eq("is_used", 0)
                .gt("expire_time", LocalDateTime.now());
        SmsVerificationCode code = getOne(queryWrapper);

        // 验证码不存在
        if(code == null){
            return false;
        }

        // 存在，判断验证码是否正确
        if(!code.getVerificationCode().equals(verifyCodeDTO.getVerificationCode())){
            return false;
        }

        // 返回true，并且将验证码标记为已使用，更新数据库
        code.setIsUsed(1);
        updateById(code);
        return null;
    }
}
