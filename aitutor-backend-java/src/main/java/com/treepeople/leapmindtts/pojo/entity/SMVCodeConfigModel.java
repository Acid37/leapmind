package com.treepeople.leapmindtts.pojo.entity;

import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.context.annotation.Configuration;

/**
 * @author : [WangWei]
 * @version : [v1.0]
 * @className : SMSConfigModel
 * @description : [短信验证码配置类]
 * @createTime : [2022/11/5 16:06]
 * @updateUser : [WangWei]
 * @updateTime : [2022/11/5 16:06]
 * @updateRemark : [描述说明本次修改内容]
 */
@Data
@RefreshScope
@Configuration
public class SMVCodeConfigModel {

    //短信签名名称
    @Value("${aliyun.sms.sMVCode.signName}")
    private String signName;
    //短信模板CODE
    @Value("${aliyun.sms.sMVCode.templateCode}")
    private String templateCode;
    //短信模板变量对应的实际值
    private String templateParam;
    //短信验证码存储在redis中的时间 单位分钟
    @Value("${aliyun.sms.sMVCode.limitTime}")
    private  String limitTime;

}

