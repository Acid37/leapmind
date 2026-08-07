package com.treepeople.leapmindtts.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.treepeople.leapmindtts.pojo.entity.PracticeAnswerRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface PracticeAnswerRecordMapper extends BaseMapper<PracticeAnswerRecord> {

    @Select("""
            <script>
            SELECT r.user_id, CAST(COALESCE(SUM(r.points), 0) AS SIGNED) AS points
            FROM practice_answer_records r
            INNER JOIN practice_user_stats s ON s.user_id = r.user_id
            WHERE COALESCE(s.leaderboard_hidden, 0) = 0
              AND r.answered_at &gt;= #{startTime}
            <if test="track != null and track != ''">
              AND r.track = #{track}
            </if>
            GROUP BY r.user_id
            ORDER BY points DESC, r.user_id ASC
            LIMIT 20
            </script>
            """)
    List<PracticeAnswerRecord> selectLeaderboardScores(
            @Param("track") String track,
            @Param("startTime") LocalDateTime startTime);
}
