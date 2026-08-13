package com.treepeople.leapmindtts.profile;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class M6TestDatabaseScriptContractTest {
  private static final Path SCRIPT = Path.of("..", "scripts", "database", "m6-event-integration-test.sql");

  @Test
  void createsAnIsolatedNonDestructiveTestDatabaseWithAllStandardEvents() throws IOException {
    assertTrue(Files.exists(SCRIPT), "缺少画像事件联调测试数据库脚本");
    String sql = Files.readString(SCRIPT, StandardCharsets.UTF_8);

    assertTrue(sql.contains("CREATE DATABASE IF NOT EXISTS leapmind_event_test"));
    assertTrue(sql.contains("USE leapmind_event_test"));
    assertFalse(sql.toUpperCase().contains("DROP DATABASE"));
    assertFalse(sql.toUpperCase().contains("DROP TABLE"));

    List.of("users", "user_events", "user_profiles", "user_knowledge_mastery")
        .forEach(table -> assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS " + table), table));

    List.of(
        "answer_question",
        "finish_practice",
        "wrong_question_changed",
        "request_explanation",
        "explanation_feedback",
        "weak_point_changed",
        "lecture_interact",
        "lesson_material_used",
        "mark_reviewed",
        "preference_changed",
        "ask_doubt"
    ).forEach(eventType -> assertTrue(sql.contains("'" + eventType + "'"), eventType));

    assertTrue(sql.contains("ON DUPLICATE KEY UPDATE event_id = VALUES(event_id)"));
  }
}
