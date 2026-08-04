package com.treepeople.leapmindtts.pojo.vo;

import com.treepeople.leapmindtts.pojo.enums.GradeEnum;
import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

/**
 * 用户视图对象
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserVO {
    
    private Long id;
    
    private String username;

    private String identify;
    
    private GradeEnum grade;

    private String stage;
    
    private String studentName;
    
    private String email;
    
    private String phone;
    
    private Integer status;
    
    private LocalDateTime createdAt;

}