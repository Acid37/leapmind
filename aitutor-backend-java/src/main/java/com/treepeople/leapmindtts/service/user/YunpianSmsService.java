package com.treepeople.leapmindtts.service.user;

import com.treepeople.leapmindtts.pojo.entity.YunpianSmsRequest;
import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.io.IOException;
import java.util.Objects;

/**
 * 云片短信服务实现
 */
@Service
public class YunpianSmsService {

    @Resource
    private OkHttpClient okHttpClient;

    /** 从配置文件读取apikey */
    @Value("${yunpian.sms.api-key}")
    private String apikey;

    /** 从配置文件读取接口地址 */
    @Value("${yunpian.sms.url}")
    private String smsUrl;

    /** 从配置文件读取默认签名 */
    @Value("${yunpian.sms.sign}")
    private String defaultSign;

    /**
     * 发送短信（核心方法）
     * @param mobile 手机号（不带+86）
     * @param content 短信内容（可不带签名，自动拼接默认签名）
     * @param request 可选扩展参数（extend、uid等，可为null）
     * @return 云片返回的响应结果（JSON字符串）
     * @throws IOException 网络异常
     */
    public String sendSms(String mobile, String content, YunpianSmsRequest request) throws IOException {
        // 1. 构建完整请求参数
        YunpianSmsRequest smsRequest = Objects.nonNull(request) ? request : new YunpianSmsRequest();
        smsRequest.setApikey(apikey); // 注入apikey
        smsRequest.setMobile(mobile); // 设置手机号

        // 2. 处理短信内容（如果未包含签名，自动拼接默认签名）
        String smsText = content;
        if (!smsText.startsWith("【") || !smsText.contains("】")) {
            smsText = defaultSign + smsText;
        }
        smsRequest.setText(smsText);

        // 3. 构建表单参数（Content-Type: application/x-www-form-urlencoded）
        FormBody.Builder formBuilder = new FormBody.Builder();
        if (smsRequest.getApikey() != null) formBuilder.add("apikey", smsRequest.getApikey());
        if (smsRequest.getMobile() != null) formBuilder.add("mobile", smsRequest.getMobile());
        if (smsRequest.getText() != null) formBuilder.add("text", smsRequest.getText());
        if (smsRequest.getExtend() != null) formBuilder.add("extend", smsRequest.getExtend());
        if (smsRequest.getUid() != null) formBuilder.add("uid", smsRequest.getUid());
        if (smsRequest.getCallbackUrl() != null) formBuilder.add("callback_url", smsRequest.getCallbackUrl());
        if (smsRequest.getRegister() != null) formBuilder.add("register", smsRequest.getRegister().toString());
        if (smsRequest.getMobileStat() != null) formBuilder.add("mobile_stat", smsRequest.getMobileStat().toString());

        // 4. 构建HTTP请求（设置请求头）
        Request httpRequest = new Request.Builder()
                .url(smsUrl)
                .post(formBuilder.build())
                .addHeader("Accept", "application/json;charset=utf-8")
                .addHeader("Content-Type", "application/x-www-form-urlencoded;charset=utf-8")
                .build();

        // 5. 执行请求并获取响应
        try (Response response = okHttpClient.newCall(httpRequest).execute()) {
            // 6. 解析响应结果（云片返回JSON格式）
            String responseBody = Objects.requireNonNull(response.body()).string();
            if (response.isSuccessful()) {
                return responseBody; // 成功返回响应内容
            } else {
                // 失败抛出异常（包含响应码和响应内容）
                throw new IOException("云片短信发送失败，响应码：" + response.code() + "，响应内容：" + responseBody);
            }
        }
    }

    /**
     * 简化版发送方法（仅需手机号和短信内容）
     */
    public String sendSms(String mobile, String content) throws IOException {
        return sendSms(mobile, content, null);
    }
}
