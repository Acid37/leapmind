package com.treepeople.leapmindtts.service.user;

import com.aliyun.dysmsapi20170525.models.SendSmsRequest;
import com.aliyun.dysmsapi20170525.models.SendSmsResponse;

/**
 * @ Package：com.treepeople.leapmindtts.service
 * @ Project：leapmind-tts - 语音分段
 * @ Description:
 * @ Date：2025/11/3  21:46
 */
public interface SmsConfigService {

    SendSmsResponse sendShortMessage(SendSmsRequest sendSmsRequest) throws Exception;

    SendSmsResponse sendSmsResponse(String phoneNumber, String verificationCode) throws Exception;
}
