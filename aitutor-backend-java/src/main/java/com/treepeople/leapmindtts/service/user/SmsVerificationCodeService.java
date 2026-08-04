package com.treepeople.leapmindtts.service.user;

import com.baomidou.mybatisplus.extension.service.IService;
import com.treepeople.leapmindtts.pojo.dto.VerifyCodeDTO;
import com.treepeople.leapmindtts.pojo.entity.SmsVerificationCode;
import jakarta.validation.Valid;

/**
 * @ Package：com.treepeople.leapmindtts.service
 * @ Project：leapmind-tts - 语音分段
 * @ Description:
 * @ Date：2025/11/4  09:45
 */
public interface SmsVerificationCodeService extends IService<SmsVerificationCode>    {


    // 根据手机号查询验证码
    Boolean verifyCode(@Valid VerifyCodeDTO verifyCodeDTO);
}
