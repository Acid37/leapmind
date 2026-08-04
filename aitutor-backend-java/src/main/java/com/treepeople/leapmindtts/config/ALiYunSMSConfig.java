package com.treepeople.leapmindtts.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.context.annotation.Configuration;

/**
 * @author : [WangWei]
 * @version : [v1.0]
 * @className : ALiYunSMSConfig
 * @description : [阿里云短信服务配置类]
 * @createTime : [2022/11/7 15:39]
 * @updateUser : [WangWei]
 * @updateTime : [2022/11/7 15:39]
 * @updateRemark : [描述说明本次修改内容]
 */
@Configuration
@RefreshScope
public class ALiYunSMSConfig {
    //阿里云账号的accessKeyId
    @Value("${aliyun.sms.accessKeyId}")
    private String accessKeyId;
    //阿里云账号的accessKeySecret
    @Value("${aliyun.sms.accessKeySecret}")
    private String accessKeySecret;
    //短信服务访问的域名
    @Value("${aliyun.sms.endpoint}")
    private String endpoint;

    public com.aliyun.dysmsapi20170525.Client createClient() throws Exception {
        com.aliyun.teaopenapi.models.Config config = new com.aliyun.teaopenapi.models.Config()
                // 您的 AccessKey ID
                .setAccessKeyId(accessKeyId)
                // 您的 AccessKey Secret
                .setAccessKeySecret(accessKeySecret);
        // 访问的域名
        config.endpoint =endpoint ;
        return new com.aliyun.dysmsapi20170525.Client(config);
    }
}
