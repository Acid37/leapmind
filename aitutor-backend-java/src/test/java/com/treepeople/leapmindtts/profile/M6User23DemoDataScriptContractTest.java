package com.treepeople.leapmindtts.profile;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class M6User23DemoDataScriptContractTest {
  private static final Path SCRIPT = Path.of("..", "scripts", "database", "user-23-full-demo-events.sql");

  @Test
  void generatesFreshAllModuleEventsWithoutChangingTheLoginAccount() throws IOException {
    assertTrue(Files.exists(SCRIPT), "缺少用户23的全模块展示数据脚本");
    String sql = Files.readString(SCRIPT, StandardCharsets.UTF_8);
    String upperSql = sql.toUpperCase();

    assertTrue(sql.contains("WHERE id = 23 AND username = 'm6_apifox_20260805'"));
    assertTrue(sql.contains("SET @demo_run_suffix = DATE_FORMAT(NOW(3)"));
    assertTrue(sql.contains("CONCAT('demo-u23-"));
    assertFalse(upperSql.contains("INSERT INTO USERS"));
    assertFalse(upperSql.contains("UPDATE USERS"));
    assertFalse(upperSql.contains("DELETE FROM"));
    assertFalse(upperSql.contains("DROP TABLE"));
    assertFalse(upperSql.contains("DROP DATABASE"));

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

    List.of("'M1'", "'M2'", "'M3'", "'M4'", "'M5'", "'M6'", "'M7'")
        .forEach(module -> assertTrue(sql.contains(module), module));
  }
}
