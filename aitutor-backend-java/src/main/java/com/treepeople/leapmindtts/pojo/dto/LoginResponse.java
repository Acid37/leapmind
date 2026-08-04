package com.treepeople.leapmindtts.pojo.dto;

import com.treepeople.leapmindtts.pojo.vo.UserVO;
import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

/**
 * 登录响应DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginResponse {
    
    private String token;
    
    private UserVO userInfo;
    
    private Long expiresIn;
}