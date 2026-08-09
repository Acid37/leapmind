package com.treepeople.leapmindtts.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.treepeople.leapmindtts.pojo.entity.UserProfile;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface UserProfileMapper extends BaseMapper<UserProfile> {
    @Select("SELECT user_id, profile_version, profile_status, status_reason, computed_at FROM user_profiles WHERE user_id=#{userId}")
    @Results({@Result(column="user_id",property="userId"),@Result(column="profile_version",property="profileVersion"),
            @Result(column="profile_status",property="profileStatus"),@Result(column="status_reason",property="statusReason"),
            @Result(column="computed_at",property="computedAt")})
    UserProfile selectVersionStamp(@Param("userId") Long userId);

    @Select("SELECT user_id, profile_version, profile_status, last_processed_event_id FROM user_profiles WHERE user_id=#{userId}")
    @Results({@Result(column="user_id",property="userId"),@Result(column="profile_version",property="profileVersion"),
            @Result(column="profile_status",property="profileStatus"),
            @Result(column="last_processed_event_id",property="lastProcessedEventId")})
    UserProfile selectProjectionStamp(@Param("userId") Long userId);

    @Update("UPDATE user_profiles SET profile_version=#{target.profileVersion}, profile_status=#{target.profileStatus}, "
            + "status_reason=#{target.statusReason}, grade=#{target.grade}, "
            + "preferred_content_modes_json=#{target.preferredContentModesJson}, "
            + "preferred_explanation_style=#{target.preferredExplanationStyle}, learning_pace=#{target.learningPace}, "
            + "recent_focus_json=#{target.recentFocusJson}, summary_profile=#{target.summaryProfile}, "
            + "profile_data_json=#{target.profileDataJson}, algorithm_version=#{target.algorithmVersion}, "
            + "confidence=#{target.confidence}, last_processed_event_id=#{watermark}, computed_at=#{target.computedAt} "
            + "WHERE user_id=#{target.userId} AND profile_version=#{baseVersion}")
    int casProfile(@Param("target") UserProfile target, @Param("watermark") Long watermark, @Param("baseVersion") Long baseVersion);

    @Update("UPDATE user_profiles SET last_processed_event_id=#{watermark} "
            + "WHERE user_id=#{userId} AND last_processed_event_id<#{watermark}")
    int advanceWatermark(@Param("userId") Long userId, @Param("watermark") Long watermark);
}
