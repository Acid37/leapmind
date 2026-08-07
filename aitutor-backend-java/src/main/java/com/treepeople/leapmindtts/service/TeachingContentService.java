package com.treepeople.leapmindtts.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.treepeople.leapmindtts.pojo.entity.TeachingContent;

import java.util.List;

/**
 * 备课内容服务接口
 */
public interface TeachingContentService extends IService<TeachingContent> {

    /**
     * 根据用户ID查询备课列表，按创建时间倒序
     *
     * @param userId 用户ID
     * @param status 状态筛选（可选）
     * @return 备课列表
     */
    List<TeachingContent> listByUserId(Long userId, String status);

    /**
     * 根据备课ID（prep_id）查询备课内容
     *
     * @param prepId 备课ID
     * @return 备课内容
     */
    TeachingContent getByPrepId(Long prepId);

    /**
     * 根据备课ID（prep_id）删除备课内容
     *
     * @param prepId 备课ID
     * @return 删除是否成功
     */
    boolean removeByPrepId(Long prepId);
}
