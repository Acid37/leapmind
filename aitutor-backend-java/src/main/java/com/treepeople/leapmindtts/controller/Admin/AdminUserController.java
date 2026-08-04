package com.treepeople.leapmindtts.controller.admin;

import com.treepeople.leapmindtts.annotation.AdminRequired;
import com.treepeople.leapmindtts.pojo.dto.UserRegisterRequest;
import com.treepeople.leapmindtts.pojo.dto.UserUpdateRequest;
import com.treepeople.leapmindtts.pojo.entity.User;
import com.treepeople.leapmindtts.pojo.result.ApiResponse;
import com.treepeople.leapmindtts.pojo.vo.UserVO;
import com.treepeople.leapmindtts.service.user.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;


@Slf4j
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminUserController {

    private final UserService userService;

    /**
     * 创建用户
     */
    @AdminRequired(message = "创建用户需要管理员权限")
    @PostMapping("/users")
    public ResponseEntity<ApiResponse<UserVO>> createUser(@Valid @RequestBody UserRegisterRequest request) {
        log.info("管理员创建用户: {}", request.getUsername());
        UserVO userVO = userService.register(request);
        return ResponseEntity.ok(ApiResponse.success(userVO));
    }

    /**
     * 获取所有用户列表
     */
    @AdminRequired(message = "查询用户列表需要管理员权限")
    @GetMapping("/users")
    public ResponseEntity<ApiResponse<java.util.List<User>>> getAllUsers() {
        log.info("管理员查询所有用户列表");
        java.util.List<User> users = userService.getAllUsers();
        return ResponseEntity.ok(ApiResponse.success(users));
    }

    /**
     * 根据ID获取用户详情
     */
    @AdminRequired(message = "查询用户详情需要管理员权限")
    @GetMapping("/users/{id}")
    public ResponseEntity<ApiResponse<UserVO>> getUserById(@PathVariable Long id) {
        log.info("管理员查询用户详情: {}", id);
        UserVO userVO = userService.getUserById(id);
        return ResponseEntity.ok(ApiResponse.success(userVO));
    }

    /**
     * 根据姓名查询用户
     */
    @AdminRequired(message = "根据姓名查询用户需要管理员权限")
    @GetMapping("/users/search/name")
    public ResponseEntity<ApiResponse<User>> getUserByName(@RequestParam String studentName) {
        log.info("管理员根据姓名查询用户: {}", studentName);
        User user = userService.findByStudentName(studentName);
        return ResponseEntity.ok(ApiResponse.success(user));
    }

    /**
     * 根据阶段查询用户列表
     */
    @AdminRequired(message = "根据阶段查询用户需要管理员权限")
    @GetMapping("/users/search/stage")
    public ResponseEntity<ApiResponse<java.util.List<User>>> getUsersByStage(@RequestParam String stage) {
        log.info("管理员根据阶段查询用户: {}", stage);
        java.util.List<User> users = userService.findByStage(stage);
        return ResponseEntity.ok(ApiResponse.success(users));
    }

    /**
     * 更新用户信息
     */
    @AdminRequired(message = "更新用户信息需要管理员权限")
    @PutMapping("/users/{id}")
    public ResponseEntity<ApiResponse<UserVO>> updateUser(@PathVariable Long id,
                                                         @Valid @RequestBody UserUpdateRequest request) {
        log.info("管理员更新用户信息: {}", id);
        UserVO userVO = userService.updateUser(id, request);
        return ResponseEntity.ok(ApiResponse.success(userVO));
    }

    /**
     * 删除用户
     */
    @AdminRequired(message = "删除用户需要管理员权限")
    @DeleteMapping("/users/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteUser(@PathVariable Long id) {
        log.info("管理员删除用户: {}", id);
        userService.deleteUser(id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

}
