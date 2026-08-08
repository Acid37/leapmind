 package com.treepeople.leapmindtts.controller.lesson;
 
 import com.treepeople.leapmindtts.pojo.result.ApiResponse;
 import com.treepeople.leapmindtts.pojo.vo.EducationStageVO;
 import com.treepeople.leapmindtts.pojo.vo.GradeVO;
 import com.treepeople.leapmindtts.service.lesson.EducationStageService;
 import jakarta.validation.constraints.Pattern;
 import lombok.RequiredArgsConstructor;
 import lombok.extern.slf4j.Slf4j;
 import org.springframework.validation.annotation.Validated;
 import org.springframework.web.bind.annotation.*;
 
 import java.util.List;
 
 /**
  * 教育阶段控制器
  * 处理教育阶段和年级相关的查询操作
  */
 @Slf4j
 @RestController
 @RequestMapping("/api/education")
 @RequiredArgsConstructor
 @Validated // 1. 开启 Controller 级别的参数安全校验
 public class EducationStageController {
 
     private final EducationStageService educationStageService;
 
     /**
      * 查询所有教育阶段
      */
     @GetMapping("/stages")
     public ApiResponse<List<EducationStageVO>> getAllStages() {
         // 2. 移除冗余的 try-catch，让业务/系统异常自然向上抛出，由全局异常处理器做脱敏过滤
         List<EducationStageVO> stages = educationStageService.getAllStages();
         return ApiResponse.success(stages, "查询教育阶段成功");
     }
 
     /**
      * 根据阶段代码查询年级列表
      */
     @GetMapping("/stages/{stageCode}/grades")
     public ApiResponse<List<GradeVO>> getGradesByStage(
             @PathVariable("stageCode") // 3. 显式指定名称，防 Spring Boot 3.2+ 报错
             @Pattern(regexp = "^[a-zA-Z0-9_]{2,20}$", message = "阶段代码格式不正确") // 4. 正则白名单防御，拦截非法恶意参数
             String stageCode) {
         List<GradeVO> grades = educationStageService.getGradesByStage(stageCode);
         return ApiResponse.success(grades, "查询年级列表成功");
     }
 }