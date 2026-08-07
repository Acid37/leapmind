package com.treepeople.leapmindtts.service.lesson.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.treepeople.leapmindtts.exception.M4LectureException;
import com.treepeople.leapmindtts.mapper.LectureMapper;
import com.treepeople.leapmindtts.pojo.dto.LectureCreateRequest;
import com.treepeople.leapmindtts.pojo.entity.Lecture;
import com.treepeople.leapmindtts.pojo.enums.LectureStatus;
import com.treepeople.leapmindtts.pojo.vo.LecturePageVO;
import com.treepeople.leapmindtts.pojo.vo.LectureVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * M4 讲课服务单元测试
 * <p>
 * 覆盖 LectureServiceImpl 的 CRUD、分页返回 {total, items} 结构、
 * 以及业务异常（404 not found）路径。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("M4 讲课服务单元测试")
class LectureServiceImplTest {

    @Mock
    private LectureMapper lectureMapper;

    @InjectMocks
    private LectureServiceImpl lectureService;

    private Lecture sampleLecture;

    @BeforeEach
    void setUp() {
        sampleLecture = Lecture.builder()
                .id(1L)
                .courseId("math_grade8_001")
                .title("勾股定理")
                .status(LectureStatus.GENERATING.name())
                .currentPage(0)
                .progressMs(0L)
                .createdAt(LocalDateTime.of(2026, 8, 6, 10, 0))
                .updatedAt(LocalDateTime.of(2026, 8, 6, 10, 0))
                .build();
    }

    // ========== createLecture ==========

    @Test
    @DisplayName("创建讲课：初始状态为 GENERATING，插入并返回 VO")
    void createLecture_shouldInsertAndReturnVo() {
        LectureCreateRequest request = new LectureCreateRequest("math_grade8_001", "勾股定理");

        LectureVO vo = lectureService.createLecture(request);

        // 验证插入的实体
        ArgumentCaptor<Lecture> captor = ArgumentCaptor.forClass(Lecture.class);
        verify(lectureMapper).insert(captor.capture());
        Lecture inserted = captor.getValue();
        assertThat(inserted.getCourseId()).isEqualTo("math_grade8_001");
        assertThat(inserted.getTitle()).isEqualTo("勾股定理");
        assertThat(inserted.getStatus()).isEqualTo(LectureStatus.GENERATING.name());

        // 验证返回的 VO
        assertThat(vo).isNotNull();
        assertThat(vo.getCourseId()).isEqualTo("math_grade8_001");
        assertThat(vo.getTitle()).isEqualTo("勾股定理");
    }

    // ========== getByCourseId ==========

    @Test
    @DisplayName("查询讲课：存在时返回 VO")
    void getByCourseId_whenExists_shouldReturnVo() {
        when(lectureMapper.selectByCourseId("math_grade8_001")).thenReturn(sampleLecture);

        LectureVO vo = lectureService.getByCourseId("math_grade8_001");

        assertThat(vo.getId()).isEqualTo(1L);
        assertThat(vo.getCourseId()).isEqualTo("math_grade8_001");
        assertThat(vo.getStatus()).isEqualTo(LectureStatus.GENERATING.name());
    }

    @Test
    @DisplayName("查询讲课：不存在时抛 M4LectureException(404)")
    void getByCourseId_whenNotExists_shouldThrowNotFound() {
        when(lectureMapper.selectByCourseId("missing")).thenReturn(null);

        assertThatThrownBy(() -> lectureService.getByCourseId("missing"))
                .isInstanceOf(M4LectureException.class)
                .hasMessageContaining("讲课内容不存在")
                .satisfies(e -> assertThat(((M4LectureException) e).getStatus().value()).isEqualTo(404));
    }

    // ========== listAll ==========

    @Test
    @DisplayName("分页查询：返回 {total, items} 结构")
    void listAll_shouldReturnPageStructure() {
        Page<Lecture> pageResult = new Page<>(1, 20);
        pageResult.setTotal(1);
        pageResult.setRecords(Collections.singletonList(sampleLecture));
        when(lectureMapper.selectPage(any(Page.class), isNull())).thenReturn(pageResult);

        LecturePageVO result = lectureService.listAll(1, 20);

        assertThat(result.getTotal()).isEqualTo(1);
        assertThat(result.getItems()).hasSize(1);
        assertThat(result.getItems().get(0).getTitle()).isEqualTo("勾股定理");
    }

    @Test
    @DisplayName("分页查询：空数据返回 total=0、空列表")
    void listAll_whenEmpty_shouldReturnEmptyPage() {
        Page<Lecture> pageResult = new Page<>(1, 20);
        pageResult.setTotal(0);
        pageResult.setRecords(Collections.emptyList());
        when(lectureMapper.selectPage(any(Page.class), isNull())).thenReturn(pageResult);

        LecturePageVO result = lectureService.listAll(1, 20);

        assertThat(result.getTotal()).isZero();
        assertThat(result.getItems()).isEmpty();
    }

    // ========== deleteByCourseId ==========

    @Test
    @DisplayName("删除讲课：存在时按 id 删除")
    void deleteByCourseId_whenExists_shouldDelete() {
        when(lectureMapper.selectByCourseId("math_grade8_001")).thenReturn(sampleLecture);

        lectureService.deleteByCourseId("math_grade8_001");

        verify(lectureMapper).deleteById(1L);
    }

    @Test
    @DisplayName("删除讲课：不存在时抛 M4LectureException(404)")
    void deleteByCourseId_whenNotExists_shouldThrowNotFound() {
        when(lectureMapper.selectByCourseId("missing")).thenReturn(null);

        assertThatThrownBy(() -> lectureService.deleteByCourseId("missing"))
                .isInstanceOf(M4LectureException.class)
                .satisfies(e -> assertThat(((M4LectureException) e).getStatus().value()).isEqualTo(404));
    }

    // ========== updateGeneratedContent ==========

    @Test
    @DisplayName("更新生成内容：更新成功返回")
    void updateGeneratedContent_whenExists_shouldUpdate() {
        when(lectureMapper.updateGeneratedContent(eq("math_grade8_001"), any(), any(), any(), any()))
                .thenReturn(1);

        lectureService.updateGeneratedContent("math_grade8_001", "path/ppt.json", "content", 10, 600000L);

        verify(lectureMapper).updateGeneratedContent(eq("math_grade8_001"),
                eq("path/ppt.json"), eq("content"), eq(10), eq(600000L));
    }

    @Test
    @DisplayName("更新生成内容：影响行数为 0 时抛 M4LectureException(404)")
    void updateGeneratedContent_whenNotExists_shouldThrowNotFound() {
        when(lectureMapper.updateGeneratedContent(any(), any(), any(), any(), any()))
                .thenReturn(0);

        assertThatThrownBy(() -> lectureService.updateGeneratedContent("missing", "p", "c", 1, 1L))
                .isInstanceOf(M4LectureException.class)
                .satisfies(e -> assertThat(((M4LectureException) e).getStatus().value()).isEqualTo(404));
    }
}
