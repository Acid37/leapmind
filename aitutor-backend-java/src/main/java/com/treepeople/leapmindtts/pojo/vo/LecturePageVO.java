package com.treepeople.leapmindtts.pojo.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 讲课历史分页结果
 * <p>
 * 对应前端约定的 {@code { total, items }} 结构（见 M4_前端对接清单 §4.2）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LecturePageVO {

    private long total;

    private List<LectureVO> items;
}
