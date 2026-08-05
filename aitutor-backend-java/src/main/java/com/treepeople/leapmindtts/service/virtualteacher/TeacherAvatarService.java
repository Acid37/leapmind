package com.treepeople.leapmindtts.service.virtualteacher;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.treepeople.leapmindtts.mapper.TeacherAvatarMapper;
import com.treepeople.leapmindtts.mapper.UserTeacherPreferenceMapper;
import com.treepeople.leapmindtts.pojo.dto.TeacherAvatarRequest;
import com.treepeople.leapmindtts.pojo.dto.TeacherPreferenceRequest;
import com.treepeople.leapmindtts.pojo.entity.TeacherAvatar;
import com.treepeople.leapmindtts.pojo.entity.UserTeacherPreference;
import com.treepeople.leapmindtts.pojo.vo.TeacherAvatarVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TeacherAvatarService {
    private final TeacherAvatarMapper avatarMapper;
    private final UserTeacherPreferenceMapper preferenceMapper;

    public List<TeacherAvatarVO> listEnabled() {
        return avatarMapper.selectList(new LambdaQueryWrapper<TeacherAvatar>()
                        .eq(TeacherAvatar::getEnabled, true)
                        .orderByAsc(TeacherAvatar::getSortOrder)
                        .orderByAsc(TeacherAvatar::getId))
                .stream()
                .map(avatar -> toVO(avatar, null))
                .toList();
    }

    public List<TeacherAvatarVO> listAll() {
        return avatarMapper.selectList(new LambdaQueryWrapper<TeacherAvatar>()
                        .orderByAsc(TeacherAvatar::getSortOrder)
                        .orderByAsc(TeacherAvatar::getId))
                .stream()
                .map(avatar -> toVO(avatar, null))
                .toList();
    }

    public TeacherAvatarVO getPreference(Long userId) {
        UserTeacherPreference preference = preferenceMapper.selectOne(
                new LambdaQueryWrapper<UserTeacherPreference>()
                        .eq(UserTeacherPreference::getUserId, userId)
                        .last("LIMIT 1"));
        if (preference != null) {
            TeacherAvatar avatar = avatarMapper.selectById(preference.getAvatarId());
            if (avatar != null && Boolean.TRUE.equals(avatar.getEnabled())) {
                return toVO(avatar, preference);
            }
        }

        TeacherAvatar defaultAvatar = avatarMapper.selectOne(new LambdaQueryWrapper<TeacherAvatar>()
                .eq(TeacherAvatar::getEnabled, true)
                .orderByAsc(TeacherAvatar::getSortOrder)
                .last("LIMIT 1"));
        return defaultAvatar == null ? null : toVO(defaultAvatar, null);
    }

    @Transactional
    public TeacherAvatarVO savePreference(Long userId, TeacherPreferenceRequest request) {
        TeacherAvatar avatar = findByCode(request.getAvatarId());
        if (!Boolean.TRUE.equals(avatar.getEnabled())) {
            throw new IllegalArgumentException("教师形象已停用");
        }

        UserTeacherPreference preference = preferenceMapper.selectOne(
                new LambdaQueryWrapper<UserTeacherPreference>()
                        .eq(UserTeacherPreference::getUserId, userId)
                        .last("LIMIT 1"));
        if (preference == null) {
            preference = new UserTeacherPreference();
            preference.setUserId(userId);
            preference.setAvatarId(avatar.getId());
            preference.setVoiceType(defaultVoice(request.getVoiceType(), avatar.getVoiceType()));
            preference.setSpeed(defaultSpeed(request.getSpeed()));
            preferenceMapper.insert(preference);
        } else {
            preference.setAvatarId(avatar.getId());
            preference.setVoiceType(defaultVoice(request.getVoiceType(), avatar.getVoiceType()));
            preference.setSpeed(defaultSpeed(request.getSpeed()));
            preferenceMapper.updateById(preference);
        }
        return toVO(avatar, preference);
    }

    public TeacherAvatarVO create(TeacherAvatarRequest request) {
        TeacherAvatar avatar = new TeacherAvatar();
        copy(request, avatar);
        avatarMapper.insert(avatar);
        return toVO(avatar, null);
    }

    public TeacherAvatarVO update(Long id, TeacherAvatarRequest request) {
        TeacherAvatar avatar = requireAvatar(id);
        copy(request, avatar);
        avatarMapper.updateById(avatar);
        return toVO(avatar, null);
    }

    @Transactional
    public void delete(Long id) {
        requireAvatar(id);
        Long usageCount = preferenceMapper.selectCount(
                new LambdaQueryWrapper<UserTeacherPreference>()
                        .eq(UserTeacherPreference::getAvatarId, id));
        if (usageCount > 0) {
            throw new IllegalStateException("该形象仍被用户使用，请先停用而不是删除");
        }
        avatarMapper.deleteById(id);
    }

    private TeacherAvatar findByCode(String code) {
        TeacherAvatar avatar = avatarMapper.selectOne(new LambdaQueryWrapper<TeacherAvatar>()
                .eq(TeacherAvatar::getAvatarCode, code)
                .last("LIMIT 1"));
        if (avatar == null) {
            throw new IllegalArgumentException("教师形象不存在: " + code);
        }
        return avatar;
    }

    private TeacherAvatar requireAvatar(Long id) {
        TeacherAvatar avatar = avatarMapper.selectById(id);
        if (avatar == null) {
            throw new IllegalArgumentException("教师形象不存在: " + id);
        }
        return avatar;
    }

    private void copy(TeacherAvatarRequest request, TeacherAvatar avatar) {
        avatar.setAvatarCode(request.getAvatarCode());
        avatar.setName(request.getName());
        avatar.setDescription(request.getDescription());
        avatar.setModelUrl(request.getModelUrl());
        avatar.setThumbnailUrl(request.getThumbnailUrl());
        avatar.setVoiceType(request.getVoiceType());
        avatar.setAccent(request.getAccent());
        avatar.setEnabled(request.getEnabled());
        avatar.setSortOrder(request.getSortOrder());
    }

    private TeacherAvatarVO toVO(TeacherAvatar avatar, UserTeacherPreference preference) {
        return TeacherAvatarVO.builder()
                .id(avatar.getAvatarCode())
                .name(avatar.getName())
                .description(avatar.getDescription())
                .modelUrl(avatar.getModelUrl())
                .thumbnailUrl(avatar.getThumbnailUrl())
                .voiceType(preference == null ? avatar.getVoiceType() : preference.getVoiceType())
                .accent(avatar.getAccent())
                .enabled(avatar.getEnabled())
                .sortOrder(avatar.getSortOrder())
                .speed(preference == null ? BigDecimal.ONE : preference.getSpeed())
                .build();
    }

    private String defaultVoice(String requested, String fallback) {
        return requested == null || requested.isBlank() ? fallback : requested;
    }

    private BigDecimal defaultSpeed(BigDecimal speed) {
        return speed == null ? BigDecimal.ONE : speed;
    }
}
