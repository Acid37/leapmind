package com.treepeople.leapmindtts.service.lesson;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.treepeople.leapmindtts.mapper.PPTSlidesMapper;
import com.treepeople.leapmindtts.pojo.entity.PPTSlides;
import com.treepeople.leapmindtts.pojo.vo.PPTSlidesVO;
import com.treepeople.leapmindtts.service.lesson.impl.PPTSlidesServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PPTSlidesServiceImplTest {

    @Mock
    private PPTSlidesMapper mapper;

    private PPTSlidesServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new PPTSlidesServiceImpl();
        ReflectionTestUtils.setField(service, "baseMapper", mapper);
    }

    @Test
    void shouldReturnEmptyListWhenCourseHasNoSlides() {
        when(mapper.selectList(any())).thenReturn(Collections.emptyList());

        List<PPTSlidesVO> result = service.getPPTSlidesList("course-empty");

        assertThat(result).isEmpty();
    }

    @Test
    void shouldExposeSlideMetadataAndRequestStableOrdering() {
        PPTSlides slide = new PPTSlides(
                10,
                "course-1",
                2,
                "slide-2",
                "极限的定义",
                "content",
                "<p>当 x 趋近 a 时</p>",
                "2026-07-28 19:00:00"
        );
        when(mapper.selectList(any())).thenReturn(List.of(slide));

        List<PPTSlidesVO> result = service.getPPTSlidesList("course-1");

        assertThat(result).singleElement().satisfies(item -> {
            assertThat(item.getCourseId()).isEqualTo("course-1");
            assertThat(item.getSlideIndex()).isEqualTo(2);
            assertThat(item.getSlideId()).isEqualTo("slide-2");
            assertThat(item.getTitle()).isEqualTo("极限的定义");
            assertThat(item.getContentType()).isEqualTo("content");
            assertThat(item.getHtmlContent()).contains("趋近");
        });

        ArgumentCaptor<QueryWrapper<PPTSlides>> captor = ArgumentCaptor.forClass(QueryWrapper.class);
        verify(mapper).selectList(captor.capture());
        assertThat(captor.getValue().getSqlSegment())
                .contains("course_id")
                .contains("slide_index")
                .containsIgnoringCase("ORDER BY");
    }
}
