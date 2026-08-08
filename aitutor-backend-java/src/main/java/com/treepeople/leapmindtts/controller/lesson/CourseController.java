package com.treepeople.leapmindtts.controller.lesson;

import com.treepeople.leapmindtts.pojo.result.ApiResponse;
import com.treepeople.leapmindtts.pojo.vo.EducationStageVO;
import com.treepeople.leapmindtts.pojo.vo.GradeVO;
import com.treepeople.leapmindtts.service.lesson.EducationStageService;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/education")
@RequiredArgsConstructor
@Validated // 开启方法级别的参数校验
public class CourseController {

    private final EducationStageService educationStageService;

    // All conflicting methods have been removed.

}
