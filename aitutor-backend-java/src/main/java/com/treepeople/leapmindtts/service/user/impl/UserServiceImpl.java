package com.treepeople.leapmindtts.service.user.impl;

import com.aliyun.dysmsapi20170525.models.SendSmsResponse;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.treepeople.leapmindtts.config.JwtConfig;
import com.treepeople.leapmindtts.exception.AccountDisabledException;
import com.treepeople.leapmindtts.exception.InvalidCredentialsException;
import com.treepeople.leapmindtts.exception.UserNotFoundException;
import com.treepeople.leapmindtts.exception.UsernameAlreadyExistsException;
import com.treepeople.leapmindtts.mapper.UserMapper;
import com.treepeople.leapmindtts.pojo.dto.LoginResponse;
import com.treepeople.leapmindtts.pojo.dto.UserLoginRequest;
import com.treepeople.leapmindtts.pojo.dto.UserRegisterRequest;
import com.treepeople.leapmindtts.pojo.dto.UserUpdateRequest;
import com.treepeople.leapmindtts.pojo.entity.SmsVerificationCode;
import com.treepeople.leapmindtts.pojo.entity.User;
import com.treepeople.leapmindtts.pojo.vo.UserVO;
import com.treepeople.leapmindtts.service.user.SmsConfigService;
import com.treepeople.leapmindtts.service.user.SmsVerificationCodeService;
import com.treepeople.leapmindtts.service.user.UserService;
import com.treepeople.leapmindtts.util.AuthCodeUtil;
import com.treepeople.leapmindtts.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 用户服务实现类
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final JwtConfig jwtConfig;
    private final SmsConfigService smsConfigService;
    private final SmsVerificationCodeService smsVerificationCodeService;

    @Override
    @Transactional
    public UserVO register(UserRegisterRequest request) {
        // 检查用户名是否已存在
        if (existsByUsername(request.getUsername())) {
            throw new UsernameAlreadyExistsException("用户名已存在");
        }

        // 创建用户实体
        User user = User.builder()
                .username(request.getUsername())
                .password(passwordEncoder.encode(request.getPassword()))
                .grade(request.getGrade())
                .studentName(request.getStudentName())
                .email(request.getEmail())
                .phone(request.getPhone())
                .status(1) // 默认启用状态
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        // 保存用户
        userMapper.insert(user);

        log.info("用户注册成功: {}", request.getUsername());

        // 转换为VO返回
        return convertToVO(user);
    }

    @Override
    public LoginResponse login(UserLoginRequest request) {
        // 查找用户
        User user = findByUsername(request.getUsername());
        if (user == null) {
            throw new UserNotFoundException("用户不存在");
        }

        // 检查账号状态
        if (user.getStatus() != 1) {
            throw new AccountDisabledException("账号已被禁用");
        }

        // 验证密码
        if (!validatePassword(request.getPassword(), user.getPassword())) {
            throw new InvalidCredentialsException("密码错误");
        }

        // 生成JWT Token
        String token = jwtUtil.generateToken(user.getUsername(), user.getId());

        log.info("用户登录成功: {}", request.getUsername());

        // 构建登录响应
        return LoginResponse.builder()
                .token(token)
                .userInfo(convertToVO(user))
                .expiresIn(jwtConfig.getExpiration())
                .build();
    }

    @Override
    public User findByUsername(String username) {
        return userMapper.findByUsername(username);
    }

    @Override
    public UserVO getUserById(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new com.treepeople.leapmindtts.exception.UserNotFoundException("用户不存在");
        }
        return convertToVO(user);
    }

    @Override
    @Transactional
    public UserVO updateUser(Long userId, UserUpdateRequest request) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new com.treepeople.leapmindtts.exception.UserNotFoundException("用户不存在");
        }

        // 更新用户信息
        if (request.getGrade() != null) {
            user.setGrade(request.getGrade());
        }
        if (request.getStudentName() != null) {
            user.setStudentName(request.getStudentName());
        }
        if (request.getEmail() != null) {
            user.setEmail(request.getEmail());
        }
        if (request.getPhone() != null) {
            user.setPhone(request.getPhone());
        }
        if (request.getStage() != null) {
            user.setStage(request.getStage());
        }
        user.setUpdatedAt(LocalDateTime.now());

        // 保存更新
        userMapper.updateById(user);

        log.info("用户信息更新成功: {}", user.getUsername());

        return convertToVO(user);
    }

    @Override
    public boolean existsByUsername(String username) {
        return userMapper.existsByUsername(username);
    }

    @Override
    public boolean validatePassword(String rawPassword, String encodedPassword) {
        return passwordEncoder.matches(rawPassword, encodedPassword);
    }

    @Override
    public SendSmsResponse sendSmsResponse(String phoneNumber) throws Exception {
        //生成六位手机验证码
        String verificationCode = AuthCodeUtil.randomCode();
        SendSmsResponse sendSmsResponse;
        try{
            sendSmsResponse = getSmsResponse(phoneNumber, verificationCode);

        }catch (Exception e){
            throw new RuntimeException("发送短信失败");
        }
        // 发送成功，保存验证码
        smsVerificationCodeService.save(
                SmsVerificationCode
                        .builder()
                        .phone(phoneNumber)
                        .verificationCode(verificationCode)
                        .expireTime(LocalDateTime.now().plusMinutes(5))
                        .isUsed(0)
                        .build());

        return sendSmsResponse;
    }

    private SendSmsResponse getSmsResponse(String phoneNumber, String verificationCode) throws Exception {
        SendSmsResponse sendSmsResponse = getSendSmsResponse(phoneNumber, verificationCode);
        return sendSmsResponse;
    }

    private SendSmsResponse getSendSmsResponse(String phoneNumber, String verificationCode) throws Exception {
        SendSmsResponse sendSmsResponse = smsConfigService.sendSmsResponse(phoneNumber, verificationCode);
        return sendSmsResponse;
    }

    @Override
    public User existsByPhoneNumber(String phoneNumber) {
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("phone", phoneNumber);
        return userMapper.selectOne(queryWrapper);
    }

    @Override
    public User findByStudentName(String studentName) {
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("student_name", studentName);
        User user = userMapper.selectOne(queryWrapper);
        if (user == null) {
            throw new UserNotFoundException("未找到姓名为 " + studentName + " 的用户");
        }
        return user;
    }

    @Override
    public java.util.List<User> findByStage(String stage) {
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();

        // 根据阶段代码查询对应年级的用户
        if ("PRIMARY".equalsIgnoreCase(stage)) {
            // 小学阶段
            queryWrapper.in("grade", "GRADE_1", "GRADE_2", "GRADE_3", "GRADE_4", "GRADE_5", "GRADE_6");
        } else if ("JUNIOR".equalsIgnoreCase(stage)) {
            // 初中阶段
            queryWrapper.in("grade", "GRADE_7", "GRADE_8", "GRADE_9");
        } else {
            throw new IllegalArgumentException("不支持的教育阶段: " + stage + "，请使用 PRIMARY 或 JUNIOR");
        }

        return userMapper.selectList(queryWrapper);
    }

    @Override
    @Transactional
    public void deleteUser(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new UserNotFoundException("用户不存在");
        }

        int result = userMapper.deleteById(userId);
        if (result > 0) {
            log.info("用户删除成功: {}", user.getUsername());
        } else {
            throw new RuntimeException("删除用户失败");
        }
    }

    @Override
    public java.util.List<User> getAllUsers() {
        return userMapper.selectList(null);
    }

    /**
     * 将User实体转换为UserVO
     *
     * @param user 用户实体
     * @return 用户视图对象
     */
    private UserVO convertToVO(User user) {
        return UserVO.builder()
                .id(user.getId())
                .username(user.getUsername())
                .grade(user.getGrade())
                .stage(user.getStage())
                .studentName(user.getStudentName())
                .email(user.getEmail())
                .phone(user.getPhone())
                .status(user.getStatus())
                .createdAt(user.getCreatedAt())
                .identify(user.getIdentify())
                .build();
    }
}
