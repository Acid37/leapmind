package com.treepeople.leapmindtts.service.user;

import com.aliyun.dysmsapi20170525.models.SendSmsResponse;
import com.treepeople.leapmindtts.pojo.dto.*;
import com.treepeople.leapmindtts.pojo.entity.User;
import com.treepeople.leapmindtts.pojo.vo.UserVO;

/**
 * 用户服务接口
 */
public interface UserService {

    /**
     * 用户注册
     *
     * @param request 注册请求
     * @return 用户视图对象
     */
    UserVO register(UserRegisterRequest request);

    /**
     * 用户登录
     *
     * @param request 登录请求
     * @return 登录响应
     */
    LoginResponse login(UserLoginRequest request);

    /**
     * 根据用户名查询用户
     *
     * @param username 用户名
     * @return 用户实体
     */
    User findByUsername(String username);

    /**
     * 根据用户ID查询用户信息
     *
     * @param userId 用户ID
     * @return 用户视图对象
     */
    UserVO getUserById(Long userId);

    /**
     * 更新用户信息
     *
     * @param userId 用户ID
     * @param request 更新请求
     * @return 更新后的用户视图对象
     */
    UserVO updateUser(Long userId, UserUpdateRequest request);

    /**
     * 检查用户名是否存在
     *
     * @param username 用户名
     * @return 存在返回true，否则返回false
     */
    boolean existsByUsername(String username);

    /**
     * 验证密码
     *
     * @param rawPassword 原始密码
     * @param encodedPassword 加密密码
     * @return 验证结果
     */
    boolean validatePassword(String rawPassword, String encodedPassword);

    /**
     * 发送短信验证码
     * @param phoneNumber
     * @return
     * @throws Exception
     */
    SendSmsResponse sendSmsResponse(String phoneNumber) throws Exception;

    /**
     * 检查手机号是否存在
     * @param phoneNumber
     * @return
     */
    User existsByPhoneNumber(String phoneNumber);

    /**
     * 根据学生姓名查询用户
     * @param studentName 学生姓名
     * @return 用户实体
     */
    User findByStudentName(String studentName);

    /**
     * 根据教育阶段查询用户列表
     * @param stage 教育阶段 (PRIMARY-小学, JUNIOR-初中)
     * @return 用户列表
     */
    java.util.List<User> findByStage(String stage);

    /**
     * 删除用户
     * @param userId 用户ID
     */
    void deleteUser(Long userId);

    /**
     * 获取所有用户列表
     * @return 用户列表
     */
    java.util.List<User> getAllUsers();
}
