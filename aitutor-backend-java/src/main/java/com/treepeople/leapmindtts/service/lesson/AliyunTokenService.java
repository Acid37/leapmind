package com.treepeople.leapmindtts.service.lesson;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.aliyuncs.CommonRequest;
import com.aliyuncs.CommonResponse;
import com.aliyuncs.DefaultAcsClient;
import com.aliyuncs.IAcsClient;
import com.aliyuncs.exceptions.ClientException;
import com.aliyuncs.http.MethodType;
import com.aliyuncs.http.ProtocolType;
import com.aliyuncs.profile.DefaultProfile;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;

/**
 * 阿里云Token获取服务 - 使用官方SDK
 */
@Service
@Slf4j
public class AliyunTokenService {

    // 地域ID
    private static final String REGION_ID = "cn-shanghai";
    // 获取Token服务域名
    private static final String DOMAIN = "nls-meta.cn-shanghai.aliyuncs.com";
    // API版本
    private static final String API_VERSION = "2019-02-28";
    // API名称
    private static final String REQUEST_ACTION = "CreateToken";

    // 响应参数
    private static final String KEY_TOKEN = "Token";
    private static final String KEY_ID = "Id";

    @Value("${aliyun.access-key-id}")
    private String accessKeyId;

    @Value("${aliyun.access-key-secret}")
    private String accessKeySecret;

    private Mono<String> cachedTokenMono;

    public Mono<String> getToken() {
        if (this.cachedTokenMono == null) {
            this.cachedTokenMono = fetchNewToken()
                    .cache(token -> Duration.ofHours(10),
                            error -> Duration.ZERO,
                            () -> Duration.ZERO);
        }
        return this.cachedTokenMono;
    }

    private Mono<String> fetchNewToken() {
        log.info("正在从阿里云获取新的Token...");

        return Mono.fromCallable(() -> {
            try {
                // 创建DefaultAcsClient实例并初始化
                DefaultProfile profile = DefaultProfile.getProfile(
                        REGION_ID,
                        accessKeyId,
                        accessKeySecret);

                IAcsClient client = new DefaultAcsClient(profile);
                CommonRequest request = new CommonRequest();
                request.setDomain(DOMAIN);
                request.setVersion(API_VERSION);
                request.setAction(REQUEST_ACTION);
                request.setMethod(MethodType.POST);
                request.setProtocol(ProtocolType.HTTPS);

                log.debug("请求阿里云Token API...");
                CommonResponse response = client.getCommonResponse(request);

                if (response.getHttpStatus() == 200) {
                    JSONObject result = JSON.parseObject(response.getData());
                    String token = result.getJSONObject(KEY_TOKEN).getString(KEY_ID);
                    if (token != null && !token.isEmpty()) {
                        log.info("成功获取新Token: {}...", token.substring(0, Math.min(10, token.length())));
                        return token;
                    } else {
                        throw new RuntimeException("Token为空");
                    }
                } else {
                    log.error("获取Token失败！HTTP状态码: {}, 响应: {}", response.getHttpStatus(), response.getData());
                    throw new RuntimeException("获取Token失败，HTTP状态码: " + response.getHttpStatus());
                }

            } catch (ClientException e) {
                log.error("调用阿里云Token API时出错: {}", e.getMessage());
                // 网络连接问题时，记录警告但仍然抛出异常，让上层处理
                if (e.getMessage().contains("Server unreachable") || e.getMessage().contains("Connection reset")) {
                    log.warn("阿里云Token服务网络不可达，这可能导致TTS服务暂时不可用");
                }
                throw new RuntimeException("调用阿里云Token API失败: " + e.getMessage(), e);
            } catch (Exception e) {
                log.error("获取Token时出现未知错误", e);
                throw new RuntimeException("获取Token失败", e);
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }
}
