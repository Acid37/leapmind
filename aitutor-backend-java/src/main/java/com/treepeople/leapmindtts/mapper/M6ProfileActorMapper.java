package com.treepeople.leapmindtts.mapper;

import com.treepeople.leapmindtts.pojo.entity.M6ProfileActor;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** Deliberately narrow projection: M6 authorization must never hydrate full user records. */
@Mapper
public interface M6ProfileActorMapper {
    @Select("SELECT id, username, status FROM users WHERE id = #{userId}")
    M6ProfileActor selectActorById(@Param("userId") Long userId);
}
