package com.treepeople.leapmindtts.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.treepeople.leapmindtts.pojo.entity.UserProfile;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface UserProfileMapper extends BaseMapper<UserProfile> {
    @Select("SELECT user_id, profile_version, profile_status, status_reason, computed_at FROM user_profiles WHERE user_id=#{userId}")
    @Results({@Result(column="user_id",property="userId"),@Result(column="profile_version",property="profileVersion"),
            @Result(column="profile_status",property="profileStatus"),@Result(column="status_reason",property="statusReason"),
            @Result(column="computed_at",property="computedAt")})
    UserProfile selectVersionStamp(@Param("userId") Long userId);
}
