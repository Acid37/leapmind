package com.treepeople.leapmindtts.aspect;

import com.treepeople.leapmindtts.annotation.AdminRequired;
import com.treepeople.leapmindtts.exception.UnauthorizedException;
import com.treepeople.leapmindtts.pojo.vo.UserVO;
import com.treepeople.leapmindtts.service.user.UserService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 管理员权限验证切面
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class AdminAuthAspect {

    private final UserService userService;

    /**
     * 管理员身份标识配置
     * 这里定义哪些身份标识被认为是管理员
     */
    private static final String[] ADMIN_IDENTITIES = {
        "admin",
        "administrator"
    };

    @Around("@annotation(adminRequired)")
    public Object checkAdminPermission(ProceedingJoinPoint joinPoint, AdminRequired adminRequired) throws Throwable {

        // 获取当前请求
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            throw new UnauthorizedException("无法获取请求上下文");
        }

        HttpServletRequest request = attributes.getRequest();

        // 从请求中获取用户名（由JWT过滤器设置）
        String username = (String) request.getAttribute("username");
        Long userId = (Long) request.getAttribute("userId");

        if (username == null || userId == null) {
            throw new UnauthorizedException("用户未登录");
        }

        // 验证是否为管理员
        if (!isAdmin(username, userId)) {
            log.warn("非管理员用户禁止访问管理员接口: {}", username);
            throw new UnauthorizedException(adminRequired.message());
        }

        log.info("管理员权限验证通过: {}", username);

        // 继续执行原方法
        return joinPoint.proceed();
    }

    /**
     * 判断用户是否为管理员
     *
     * @param username 用户名
     * @param userId 用户ID
     * @return 是否为管理员
     */
    private boolean isAdmin(String username, Long userId) {
        if (username == null || userId == null) {
            return false;
        }

        try {
            // 从数据库查询用户信息
            UserVO user = userService.getUserById(userId);
            if (user == null) {
                log.warn("用户不存在: {}", userId);
                return false;
            }

            String identify = user.getIdentify();
            log.info("用户权限检查: username={}, userId={}, identify={}", username, userId, identify);

            // 检查身份标识是否为管理员
            if (identify != null) {
                for (String adminIdentity : ADMIN_IDENTITIES) {
                    if (adminIdentity.equalsIgnoreCase(identify)) {
                        log.info("管理员权限验证通过: username={}, identify={}", username, identify);
                        return true;
                    }
                }
            }

            log.warn("用户不具有管理员权限: username={}, identify={}", username, identify);
            return false;

        } catch (Exception e) {
            log.error("查询用户信息失败: userId={}, error={}", userId, e.getMessage());
            return false;
        }
    }
}
