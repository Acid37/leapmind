package com.treepeople.leapmindtts.pojo.dto;

import lombok.Data;

/**
 * @ Package：com.treepeople.leapmindtts.pojo.dto
 * @ Project：leapmind-tts - 语音分段
 * @ Description:
 * @ Date：2025/11/4  09:54
 */
@Data
public class VerifyCodeDTO {

    private String phoneNumber;

    private String verificationCode;

}
