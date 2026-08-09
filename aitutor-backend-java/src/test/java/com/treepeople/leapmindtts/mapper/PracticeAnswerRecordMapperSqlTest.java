package com.treepeople.leapmindtts.mapper;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.SqlSource;
import org.apache.ibatis.scripting.xmltags.XMLLanguageDriver;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PracticeAnswerRecordMapperSqlTest {

    @Test
    void leaderboardSqlAlwaysUsesTimeAndAddsTrackOnlyWhenProvided() throws Exception {
        Method method = PracticeAnswerRecordMapper.class.getMethod(
                "selectLeaderboardScores", String.class, LocalDateTime.class);
        String script = String.join(" ", method.getAnnotation(Select.class).value());
        SqlSource source = new XMLLanguageDriver().createSqlSource(
                new Configuration(), script, Map.class);

        Map<String, Object> parameters = new HashMap<>();
        parameters.put("startTime", LocalDateTime.of(2026, 8, 5, 0, 0));
        parameters.put("track", "高数期末");
        BoundSql tracked = source.getBoundSql(parameters);
        assertTrue(normalize(tracked.getSql()).contains("r.answered_at >= ?"));
        assertTrue(normalize(tracked.getSql()).contains("r.track = ?"));
        assertEquals(2, tracked.getParameterMappings().size());

        parameters.put("track", "");
        BoundSql allTracks = source.getBoundSql(parameters);
        assertTrue(normalize(allTracks.getSql()).contains("r.answered_at >= ?"));
        assertFalse(normalize(allTracks.getSql()).contains("r.track = ?"));
        assertEquals(1, allTracks.getParameterMappings().size());
    }

    private String normalize(String sql) {
        return sql.replaceAll("\\s+", " ").trim();
    }
}
