package com.treepeople.leapmindtts.service.user.impl;

import com.alibaba.fastjson2.JSON;
import com.aliyun.dysmsapi20170525.Client;
import com.aliyun.dysmsapi20170525.models.SendSmsRequest;
import com.aliyun.dysmsapi20170525.models.SendSmsResponse;
import com.aliyun.teautil.models.RuntimeOptions;
import com.treepeople.leapmindtts.config.ALiYunSMSConfig;
import com.treepeople.leapmindtts.pojo.entity.SMVCodeConfigModel;
import com.treepeople.leapmindtts.service.user.SmsConfigService;
import lombok.RequiredArgsConstructor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Service;

import java.util.HashMap;

/**
 * @author : [WangWei]
 * @version : [v1.0]
 * @className : SMSConfigServiceImpl
 * @description : [短信服务管理业务实现类]
 * @createTime : [2022/11/5 17:16]
 * @updateUser : [WangWei]
 * @updateTime : [2022/11/5 17:16]
 * @updateRemark : [描述说明本次修改内容]
 */
@Service
@RequiredArgsConstructor
public class SMSConfigServiceImpl implements SmsConfigService {
    private static final Logger log = LogManager.getLogger();
    //阿里云短信服务配置类
    private final ALiYunSMSConfig aLiYunSMSConfig;


    private final SMVCodeConfigModel smvCodeConfigModel;

    /*
     * @version V1.0
     * Title: sendShortMessage
     * @author Wangwei
     * @description 发送短信
     * @createTime  2022/11/7 16:02
     * @param [sendSmsRequest]
     * @return com.aliyun.dysmsapi20170525.models.SendSmsResponse
     */
    public SendSmsResponse sendShortMessage(SendSmsRequest sendSmsRequest) throws Exception {
        //初始化配置信息
        Client client = aLiYunSMSConfig.createClient();
        //TODO 配置运行时间选项暂时未进行配置
        RuntimeOptions runtime = new RuntimeOptions();

        SendSmsResponse sendSmsResponse;
        try {
            //发送短信
            sendSmsResponse = client.sendSmsWithOptions(sendSmsRequest, runtime);
        } catch (Exception e) {
            throw new Exception("调用阿里云发送短信接口失败", e);
        }
        log.info("调用阿里云发送短信接口成功");
        return sendSmsResponse;
    }

    public SendSmsResponse sendSmsResponse(String phoneNumber, String verificationCode) throws Exception {
        //拼接阿里云短信模板变量对应的实际值"{\"code\":\"+verificationCode+\"}";
        HashMap<String, String> hashMap = new HashMap<>();
        hashMap.put("code", verificationCode);
        String templateParam = JSON.toJSONString(hashMap);

        //配置发送阿里云短信的请求体
        SendSmsRequest sendSmsRequest = new SendSmsRequest();
        //设置短信签名名称
        sendSmsRequest.setSignName(smvCodeConfigModel.getSignName());
        //设置短信模板Code
        sendSmsRequest.setTemplateCode(smvCodeConfigModel.getTemplateCode());
        //设置发送短信的手机号
        sendSmsRequest.setPhoneNumbers(phoneNumber);
        //设置短信模板变量对应的实际值
        sendSmsRequest.setTemplateParam(templateParam);


        //调用阿里云短信服务发送短信验证码
        return sendShortMessage(sendSmsRequest);
    }
}
