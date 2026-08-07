package com.treepeople.leapmindtts.pojo.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("practice_user_stats")
public class PracticeUserStats {
    @TableId("user_id")
    private Long userId;
    @TableField("total_points")
    private Integer totalPoints;
    @TableField("total_answers")
    private Integer totalAnswers;
    @TableField("correct_answers")
    private Integer correctAnswers;
    @TableField("conquered_mistakes")
    private Integer conqueredMistakes;
    @TableField("current_streak")
    private Integer currentStreak;
    @TableField("last_practice_date")
    private LocalDate lastPracticeDate;
    @TableField("daily_bonus_date")
    private LocalDate dailyBonusDate;
    @TableField("leaderboard_hidden")
    private Boolean leaderboardHidden;
    @TableField("preferred_track")
    private String preferredTrack;
    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
