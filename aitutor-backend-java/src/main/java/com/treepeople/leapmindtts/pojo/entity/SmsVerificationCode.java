package com.treepeople.leapmindtts.pojo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@TableName("sms_verification_code")
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SmsVerificationCode {

    /**
     * 主键ID（对应表中 id 字段）
     * 类型：BIGINT UNSIGNED
     * 策略：自增（AUTO）
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 接收验证码的手机号（如：+8613800138000）
     * 类型：VARCHAR(20)
     */
    private String phone;

    /**
     * 手机验证码（通常为6位数字）
     * 类型：VARCHAR(10)
     */
    private String verificationCode;


    /**
     * 验证码有效期截止时间
     * 类型：DATETIME
     */
    private LocalDateTime expireTime;

    /**
     * 使用状态（0-未使用 1-已使用）
     * 类型：TINYINT UNSIGNED
     */
    private Integer isUsed = 0;

}
