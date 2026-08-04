package com.treepeople.leapmindtts.pojo.entity;

import lombok.Data;

/**
 * 云片短信发送请求参数
 */
@Data
public class YunpianSmsRequest {
    /** 必传：用户唯一标识apikey */
    private String apikey;

    /** 必传：接收手机号（不带+86） */
    private String mobile;

    /** 必传：短信内容（需包含签名，如【云片网】您的验证码是1234） */
    private String text;

    /** 可选：下发号码扩展号（纯数字） */
    private String extend;

    /** 可选：业务系统内的ID（如订单号） */
    private String uid;

    /** 可选：发送报告回调地址 */
    private String callbackUrl;

    /** 可选：是否为注册验证码短信（需联系客服开通） */
    private Boolean register;

    /** 可选：是否替换短链接为手机号专属链接（默认false） */
    private Boolean mobileStat = false;
}