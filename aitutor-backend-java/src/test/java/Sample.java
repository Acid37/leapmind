
import com.alibaba.fastjson2.JSON;
import com.aliyun.dysmsapi20170525.Client;
import com.aliyun.dysmsapi20170525.models.AddExtCodeSignRequest;
import com.aliyun.dysmsapi20170525.models.SendSmsRequest;
import com.aliyun.dysmsapi20170525.models.SendSmsResponse;
import com.aliyun.teaopenapi.models.Config;
import com.treepeople.leapmindtts.config.ALiYunSMSConfig;
import com.treepeople.leapmindtts.pojo.entity.SMVCodeConfigModel;
import com.treepeople.leapmindtts.service.user.SmsConfigService;
import com.treepeople.leapmindtts.util.AuthCodeUtil;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.HashMap;

import static com.alibaba.fastjson2.JSON.toJSONString;

/**
 *  
 * @ Package：PACKAGE_NAME
 * @ Project：leapmind-tts - 语音分段
 * @ Description:
 * @ Date：2025/11/3  20:29
 */
@SpringBootTest
@Slf4j
public class Sample {

    @Autowired
    private SmsConfigService smsConfigService;

    /*public static Client createClient() throws Exception {

        Config config = new Config()
                // 使用环境变量获取AccessKey
                .setAccessKeyId(System.getenv("ALIBABA_CLOUD_ACCESS_KEY_ID"))
                .setAccessKeySecret(System.getenv("ALIBABA_CLOUD_ACCESS_KEY_SECRET"));
        
        // 配置 Endpoint
        config.endpoint = "dysmsapi.aliyuncs.com";

        return new Client(config);
    }*/

    /*public static void main(String[] args) throws Exception {
        // 初始化请求客户端
        Client client = Sample.createClient();

        // 构造API请求对象，请替换请求参数值
        SendSmsRequest sendSmsRequest = new SendSmsRequest()
                .setPhoneNumbers("15322569867")
                .setSignName("验证码")  // 签名名称，不要包含【】
                .setTemplateCode("SMS_154950909")  // 请替换为你的实际模板代码
                .setTemplateParam("{\"code\":\"123456\"}"); // 模板参数，根据你的模板调整

        // 获取响应对象
        SendSmsResponse sendSmsResponse = client.sendSms(sendSmsRequest);

        // 响应包含服务端响应的 body 和 headers
        System.out.println(toJSONString(sendSmsResponse));
    }*/

    @Test
    public void test() throws Exception {

    }
}
