package com.treepeople.leapmindtts.pojo.dto;

import com.treepeople.leapmindtts.pojo.enums.GradeEnum;
import lombok.Data;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 用户信息更新请求DTO
 */
@Data
public class UserUpdateRequest {
    
    private GradeEnum grade;

    private String stage;
    
    @Size(max = 100, message = "学生姓名长度不能超过100个字符")
    private String studentName;
    
    @Email(message = "邮箱格式不正确")
    @Size(max = 100, message = "邮箱长度不能超过100个字符")
    private String email;
    
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String phone;
}