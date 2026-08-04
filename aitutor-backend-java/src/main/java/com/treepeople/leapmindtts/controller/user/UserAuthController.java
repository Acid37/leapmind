package com.treepeople.leapmindtts.controller.user;

import com.aliyun.dysmsapi20170525.models.SendSmsResponse;
import com.treepeople.leapmindtts.pojo.dto.*;
import com.treepeople.leapmindtts.pojo.entity.User;
import com.treepeople.leapmindtts.pojo.result.ApiResponse;
import com.treepeople.leapmindtts.pojo.vo.UserVO;
import com.treepeople.leapmindtts.service.user.SmsVerificationCodeService;
import com.treepeople.leapmindtts.service.user.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.beans.BeanUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;

/**
 * 用户认证控制器
 * 处理用户注册、登录、信息查询和更新等操作
 */
@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class UserAuthController {

    private final UserService userService;

    private final SmsVerificationCodeService smsVerificationCodeService;

    /**
     * 用户注册
     *
     * @param request 注册请求
     * @return 注册结果
     */
    @PostMapping("/register")
    public ResponseEntity<ApiResponse<UserVO>> register(@RequestBody @Valid UserRegisterRequest request) {
        log.info("用户注册，{}", request);
        try {
            UserVO userVO = userService.register(request);
            return ResponseEntity.ok(ApiResponse.success(userVO, "注册成功"));
        } catch (Exception e) {
            log.error("用户注册失败: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(400, e.getMessage()));
        }
    }

    /**
     * 用户登录
     *
     * @param request 登录请求 (用户名、密码)
     * @return 登录结果
     */
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(@RequestBody @Valid UserLoginRequest request) {
        log.info("用户登录，{}", request);
        try {
            LoginResponse loginResponse = userService.login(request);
            return ResponseEntity.ok(ApiResponse.success(loginResponse, "登录成功"));
        } catch (Exception e) {
            log.error("用户登录失败: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(401, e.getMessage()));
        }
    }

    /**
     * 向手机发送验证码
     *
     * @param phoneNumber
     * @return
     */
    @GetMapping("/login/sendCode")
    public ResponseEntity<ApiResponse<String>> sendCode(@RequestParam String phoneNumber) {
        log.info("短信服务发送短信验证码成功：{}", phoneNumber);
        // 查询手机号是否存在
        User user = userService.existsByPhoneNumber(phoneNumber);
        if (user == null) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(400, "用户不存在,请先注册"));
        }
        try {
            SendSmsResponse sendSmsResponse = userService.sendSmsResponse(phoneNumber);

            // 检查响应是否为空以及响应体是否为空
            if (sendSmsResponse != null && sendSmsResponse.getBody() != null) {
                if ("OK".equals(sendSmsResponse.getBody().getCode())) {
                    log.info("短信验证码发送成功: {}", sendSmsResponse);
                    return ResponseEntity.ok(ApiResponse.success("验证码发送成功"));
                } else {
                    log.error("短信验证码发送失败，错误码: {}, 错误信息: {}",
                            sendSmsResponse.getBody().getCode(),
                            sendSmsResponse.getBody().getMessage());
                    return ResponseEntity.badRequest()
                            .body(ApiResponse.error("验证码发送失败: " + sendSmsResponse.getBody().getMessage()));
                }
            } else {
                log.error("短信服务响应为空");
                return ResponseEntity.badRequest()
                        .body(ApiResponse.error("短信服务响应异常"));
            }

        } catch (Exception e) {
            log.error("调用阿里云短信服务发送短信验证码接口失败！", e);
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("验证码发送失败: " + e.getMessage()));
        }
    }


    /**
     *  手机号验证码校验
     * @param verifyCodeDTO
     * @return
     */
    @PostMapping("/login/verifyCode")
    public ResponseEntity<ApiResponse<UserVO>> verifyCode(@RequestBody @Valid VerifyCodeDTO verifyCodeDTO) {
        Boolean result = smsVerificationCodeService.verifyCode(verifyCodeDTO);
        if(!result){
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(400, "验证码错误"));
        }
        User user = userService.existsByPhoneNumber(verifyCodeDTO.getPhoneNumber());
        if(user == null){
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(400, "用户不存在，请先注册"));
        }

        UserVO userVO = new UserVO();
        BeanUtils.copyProperties(user, userVO);
        return ResponseEntity.ok(ApiResponse.success(userVO, "登录成功"));
    }


    /**
     * 获取用户信息
     *
     * @param request HTTP请求对象
     * @return 用户信息
     */
    @GetMapping("/profile")
    public ResponseEntity<ApiResponse<UserVO>> getProfile(HttpServletRequest request) {
        try {
            Long userId = (Long) request.getAttribute("userId");
            if (userId == null) {
                return ResponseEntity.status(401)
                        .body(ApiResponse.error(401, "未授权访问"));
            }

            UserVO userVO = userService.getUserById(userId);
            return ResponseEntity.ok(ApiResponse.success(userVO, "获取用户信息成功"));
        } catch (Exception e) {
            log.error("获取用户信息失败: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(400, e.getMessage()));
        }
    }

    /**
     * 更新用户信息
     *
     * @param request       HTTP请求对象
     * @param updateRequest 更新请求
     * @return 更新结果
     */
    @PutMapping("/profile")
    public ResponseEntity<ApiResponse<UserVO>> updateProfile(HttpServletRequest request,
                                                             @RequestBody @Valid UserUpdateRequest updateRequest) {
        try {
            Long userId = (Long) request.getAttribute("userId");
            if (userId == null) {
                return ResponseEntity.status(401)
                        .body(ApiResponse.error(401, "未授权访问"));
            }

            UserVO userVO = userService.updateUser(userId, updateRequest);
            return ResponseEntity.ok(ApiResponse.success(userVO, "更新用户信息成功"));
        } catch (Exception e) {
            log.error("更新用户信息失败: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(400, e.getMessage()));
        }
    }



    @GetMapping("/test")
    public String test() {
        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder()
                .url("https://www.baidu.com")

                .build();

        try {
            Response re = client.newCall(request).execute();
            return re.body().string();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }


    }

    @GetMapping("/test1")
    public ResponseEntity<ApiResponse<String>> getMethod() {

        return ResponseEntity.ok(ApiResponse.success("你好呀"));
    }
}
