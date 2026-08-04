package com.treepeople.leapmindtts.config;

import com.treepeople.leapmindtts.mapper.UserMapper;
import com.treepeople.leapmindtts.pojo.entity.User;
import com.treepeople.leapmindtts.pojo.enums.GradeEnum;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

/**
 * 数据初始化器 - 创建测试用户数据
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) throws Exception {
        // 检查是否已有用户数据
        Long userCount = userMapper.selectCount(null);
        if (userCount > 0) {
            log.info("数据库中已有 {} 个用户，跳过初始化", userCount);
            return;
        }

        log.info("开始初始化测试用户数据...");
        
        // 创建测试用户数据
        List<User> testUsers = Arrays.asList(
            // 小学用户
            createTestUser("xiaoming1", "小明1", "xiaoming1@test.com", "13800001001", GradeEnum.GRADE_1),
            createTestUser("xiaohong1", "小红1", "xiaohong1@test.com", "13800001002", GradeEnum.GRADE_1),
            createTestUser("xiaoli2", "小李2", "xiaoli2@test.com", "13800002001", GradeEnum.GRADE_2),
            createTestUser("xiaozhang2", "小张2", "xiaozhang2@test.com", "13800002002", GradeEnum.GRADE_2),
            createTestUser("xiaowang3", "小王3", "xiaowang3@test.com", "13800003001", GradeEnum.GRADE_3),
            createTestUser("xiaoliu3", "小刘3", "xiaoliu3@test.com", "13800003002", GradeEnum.GRADE_3),
            createTestUser("xiaochen4", "小陈4", "xiaochen4@test.com", "13800004001", GradeEnum.GRADE_4),
            createTestUser("xiaoyang4", "小杨4", "xiaoyang4@test.com", "13800004002", GradeEnum.GRADE_4),
            createTestUser("xiaosun5", "小孙5", "xiaosun5@test.com", "13800005001", GradeEnum.GRADE_5),
            createTestUser("xiaozhu5", "小朱5", "xiaozhu5@test.com", "13800005002", GradeEnum.GRADE_5),
            createTestUser("xiaoxu6", "小徐6", "xiaoxu6@test.com", "13800006001", GradeEnum.GRADE_6),
            createTestUser("xiaoma6", "小马6", "xiaoma6@test.com", "13800006002", GradeEnum.GRADE_6),
            
            // 初中用户
            createTestUser("zhangsan7", "张三7", "zhangsan7@test.com", "13800007001", GradeEnum.GRADE_7),
            createTestUser("lisi7", "李四7", "lisi7@test.com", "13800007002", GradeEnum.GRADE_7),
            createTestUser("wangwu7", "王五7", "wangwu7@test.com", "13800007003", GradeEnum.GRADE_7),
            createTestUser("zhaoliu8", "赵六8", "zhaoliu8@test.com", "13800008001", GradeEnum.GRADE_8),
            createTestUser("sunqi8", "孙七8", "sunqi8@test.com", "13800008002", GradeEnum.GRADE_8),
            createTestUser("zhouba8", "周八8", "zhouba8@test.com", "13800008003", GradeEnum.GRADE_8),
            createTestUser("wujiu9", "吴九9", "wujiu9@test.com", "13800009001", GradeEnum.GRADE_9),
            createTestUser("zhengshi9", "郑十9", "zhengshi9@test.com", "13800009002", GradeEnum.GRADE_9),
            
            // 管理员用户
            createAdminUser("admin", "管理员", "admin@leapmind.com", "13900000000")
        );

        // 批量插入用户
        for (User user : testUsers) {
            try {
                userMapper.insert(user);
                log.debug("创建测试用户: {} ({})", user.getStudentName(), user.getUsername());
            } catch (Exception e) {
                log.warn("创建用户失败: {}, 错误: {}", user.getUsername(), e.getMessage());
            }
        }

        log.info("测试用户数据初始化完成，共创建 {} 个用户", testUsers.size());
    }

    private User createTestUser(String username, String studentName, String email, String phone, GradeEnum grade) {
        return User.builder()
                .username(username)
                .password(passwordEncoder.encode("123456")) // 默认密码
                .studentName(studentName)
                .email(email)
                .phone(phone)
                .grade(grade)
                .identify("student")
                .status(1) // 启用状态
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    private User createAdminUser(String username, String studentName, String email, String phone) {
        return User.builder()
                .username(username)
                .password(passwordEncoder.encode("admin123")) // 管理员密码
                .studentName(studentName)
                .email(email)
                .phone(phone)
                .grade(null) // 管理员没有年级
                .identify("admin")
                .status(1) // 启用状态
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }
}