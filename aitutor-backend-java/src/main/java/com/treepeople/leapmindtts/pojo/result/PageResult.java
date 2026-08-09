package com.treepeople.leapmindtts.pojo.result;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.List;

/**
 * 通用分页响应 VO
 * <p>
 * 放在 ApiResponse.data 内层，字段与 MyBatis-Plus Page 对齐
 *
 * @param <T> 记录类型
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PageResult<T> {

    /** 总记录数 */
    private Long total;

    /** 总页数 */
    private Long pages;

    /** 当前页码 */
    private Long current;

    /** 每页数量 */
    private Long size;

    /** 当前页数据 */
    private List<T> records;
}
