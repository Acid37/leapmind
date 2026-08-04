package com.treepeople.leapmindtts.sms.api;

/**
 *  
 * @ Package：com.treepeople.leapmindtts.sms.api
 * @ Project：leapmind-tts - 语音分段
 * @ Description:
 * @ Date：2025/11/7  11:20
 */
public interface SmsCodeVerifier {

    /**
     * 验证手机号+验证码是否有效（统一接口）
     * @param phone 手机号
     * @param inputCode 用户输入的验证码
     * @return 验证结果
     */
    boolean verifyCode(String phone, String inputCode);
}
