package com.treepeople.leapmindtts.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.treepeople.leapmindtts.mapper.TeachingContentMapper;
import com.treepeople.leapmindtts.pojo.entity.TeachingContent;
import com.treepeople.leapmindtts.service.TeachingContentService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 备课内容服务实现类
 */
@Slf4j
@Service
public class TeachingContentServiceImpl extends ServiceImpl<TeachingContentMapper, TeachingContent> implements TeachingContentService {

    /**
     * 重写 save：插入后统一 prep_id = id，保证 prep_id 唯一（配合 V9 唯一索引）。
     * <p>说明：曾尝试用 DB 生成列强制 prep_id = id，但 MySQL 禁止生成列引用 AUTO_INCREMENT 列（ERROR 3109），
     * 触发器也无法读取自增后的 id，因此 DB 层强制不可行；改回 Java 侧回填。
     * 插入前将 prep_id 置空，避免沿用旧值与历史数据撞唯一约束——MySQL 唯一索引允许多个 NULL，插入后再回填为 id。</p>
     */
    @Override
    public boolean save(TeachingContent entity) {
        entity.setPrepId(null);
        boolean saved = super.save(entity);
        if (saved && entity.getId() != null) {
            entity.setPrepId(entity.getId());
            baseMapper.update(null, new LambdaUpdateWrapper<TeachingContent>()
                    .eq(TeachingContent::getId, entity.getId())
                    .set(TeachingContent::getPrepId, entity.getId()));
        }
        return saved;
    }

    @Override
    public List<TeachingContent> listByUserId(Long userId, String status, String type) {
        log.info("查询备课列表，用户ID: {}，状态: {}，类型: {}", userId, status, type);
        QueryWrapper<TeachingContent> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("user_id", userId);
        if (status != null && !status.isBlank()) {
            queryWrapper.eq("status", status);
        }
        if (type != null && !type.isBlank()) {
            queryWrapper.eq("type", type);
        }
        queryWrapper.orderByDesc("created_at");
        return baseMapper.selectList(queryWrapper);
    }

    @Override
    public TeachingContent getByPrepId(Long prepId) {
        log.info("根据 prep_id 查询备课，prepId: {}", prepId);
        return baseMapper.selectByPrepId(prepId);
    }

    @Override
    public boolean removeByPrepId(Long prepId) {
        log.info("根据 prep_id 删除备课，prepId: {}", prepId);
        QueryWrapper<TeachingContent> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("prep_id", prepId);
        return baseMapper.delete(queryWrapper) > 0;
    }
}
