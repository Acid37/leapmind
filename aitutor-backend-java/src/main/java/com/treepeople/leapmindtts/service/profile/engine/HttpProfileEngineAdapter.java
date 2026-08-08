package com.treepeople.leapmindtts.service.profile.engine;

import com.treepeople.leapmindtts.config.PythonServiceProperties;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

/**
 * Java → Python 画像引擎 HTTP 适配器，通过
 * {@code POST /api/internal/ai/build-profile} 调用无状态引擎。
 *
 * <p>Spring 自动发现该 Bean 后，{@code @ConditionalOnMissingBean}
 * 将选用本实现替换默认的 {@link DisabledProfileEngineAdapter}。</p>
 */
@Slf4j
@Component
public final class HttpProfileEngineAdapter implements ProfileEnginePort {

    private final RestTemplate restTemplate;
    private final StrictProfileEngineJsonCodec codec;
    private final String buildProfileUrl;

    public HttpProfileEngineAdapter(PythonServiceProperties props) {
        this.buildProfileUrl = props.getBaseUrl() + props.getBuildProfilePath();
        this.restTemplate = new RestTemplate();
        this.codec = new StrictProfileEngineJsonCodec();
        log.info("M6 画像引擎已连接：{}", buildProfileUrl);
    }

    @Override
    public ProfileEngineResponse buildProfile(ProfileEngineRequest request) {
        try {
            byte[] body = codec.writeRequest(request);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<byte[]> entity = new HttpEntity<>(body, headers);

            ResponseEntity<byte[]> resp = restTemplate.postForEntity(buildProfileUrl, entity, byte[].class);

            if (!resp.getStatusCode().is2xxSuccessful() || resp.getBody() == null) {
                log.error("Python 引擎返回 HTTP {}", resp.getStatusCode().value());
                throw new ProfileEngineUnavailableException(
                        "Python engine HTTP " + resp.getStatusCode().value());
            }

            ProfileEngineResponse response = codec.readAndValidateResponse(request, resp.getBody());
            log.info("M6 画像计算完成：requestId={}, status={}, userId={}",
                    request.requestId(), response.status(), request.userId());
            return response;

        } catch (IOException e) {
            log.error("M6 JSON 编解码失败：requestId={}", request.requestId(), e);
            throw new ProfileEngineUnavailableException("JSON_CODEC_ERROR");
        } catch (ProfileEngineUnavailableException e) {
            throw e;
        } catch (Exception e) {
            log.error("M6 引擎 HTTP 调用失败：requestId={}", request.requestId(), e);
            throw new ProfileEngineUnavailableException("HTTP_ERROR");
        }
    }
}
