package com.treepeople.leapmindtts.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.treepeople.leapmindtts.pojo.entity.SMVCodeConfigModel;
import com.treepeople.leapmindtts.pojo.entity.SmsVerificationCode;
import org.apache.ibatis.annotations.Mapper;

/**
 * @ Package：com.treepeople.leapmindtts.mapper
 * @ Project：leapmind-tts - 语音分段
 * @ Description:
 * @ Date：2025/11/4  09:45
 */
@Mapper
public interface SmsVerificationCodeMapper extends BaseMapper<SmsVerificationCode> {
}
