package com.treepeople.leapmindtts.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * PythonApiClient 单元测试
 *
 * 验证 knowledge_point_names 知识点名称透传逻辑：
 *   Java LessonPrepRequest.knowledgePointNames
 *     → Jackson @JsonProperty("knowledge_point_names") 序列化为 snake_case
 *     → Python LessonPrepInternalRequest.knowledge_point_names 接收
 */
@DisplayName("PythonApiClient 知识点名称透传测试")
class PythonApiClientTest {

    private final ObjectMapper om = new ObjectMapper();

    // =========================================================================
    //  序列化测试：Java camelCase → JSON snake_case
    // =========================================================================

    @Test
    @DisplayName("LessonPrepRequest 序列化时 knowledgePointNames → knowledge_point_names")
    void lessonPrepRequest_serializesKnowledgePointNames_toSnakeCase() throws Exception {
        PythonApiClient.LessonPrepRequest request = new PythonApiClient.LessonPrepRequest();
        request.setUserId(1001);
        request.setTitle("勾股定理");
        request.setSubject("math");
        request.setGrade("grade_8");
        request.setKnowledgePointIds(List.of(20, 21, 22));
        request.setKnowledgePointNames(List.of("勾股定理", "勾股定理的逆定理", "勾股数"));
        request.setTeachingGoals(List.of("理解勾股定理"));
        request.setTotalHours(1);
        request.setStyle("standard");

        String json = om.writeValueAsString(request);

        // 验证 JSON 中包含 snake_case 的 knowledge_point_names
        assertTrue(json.contains("\"knowledge_point_names\""),
                "JSON 应包含 knowledge_point_names 字段");
        assertTrue(json.contains("勾股定理"),
                "JSON 应包含知识点名称 '勾股定理'");
        assertTrue(json.contains("勾股定理的逆定理"),
                "JSON 应包含知识点名称 '勾股定理的逆定理'");
        assertTrue(json.contains("勾股数"),
                "JSON 应包含知识点名称 '勾股数'");

        // 验证不包含 camelCase 的 knowledgePointNames
        assertFalse(json.contains("knowledgePointNames"),
                "JSON 不应包含 camelCase 的 knowledgePointNames");
    }

    @Test
    @DisplayName("LessonPrepRequest 不设 names 时 JSON 中 knowledge_point_names 为 null")
    void lessonPrepRequest_withNullNames_serializesAsNull() throws Exception {
        PythonApiClient.LessonPrepRequest request = new PythonApiClient.LessonPrepRequest();
        request.setUserId(1001);
        request.setTitle("test");
        request.setSubject("math");
        request.setGrade("grade_8");
        request.setKnowledgePointIds(List.of(20));
        request.setTeachingGoals(List.of());
        request.setTotalHours(1);
        request.setStyle("standard");

        String json = om.writeValueAsString(request);

        // Jackson 默认包含 null 字段
        assertTrue(json.contains("\"knowledge_point_names\":null"),
                "JSON 应包含 knowledge_point_names:null");
    }

    // =========================================================================
    //  往返测试：序列化 → 反序列化
    // =========================================================================

    @Test
    @DisplayName("LessonPrepRequest 序列化→反序列化往返一致")
    void lessonPrepRequest_roundTrip_preservesKnowledgePointNames() throws Exception {
        PythonApiClient.LessonPrepRequest original = new PythonApiClient.LessonPrepRequest();
        original.setUserId(1001);
        original.setTitle("一元二次方程");
        original.setSubject("math");
        original.setGrade("grade_9");
        original.setKnowledgePointIds(List.of(20, 21, 22));
        original.setKnowledgePointNames(List.of("一元二次方程", "求根公式", "韦达定理"));
        original.setTeachingGoals(List.of("理解定义", "掌握求根公式"));
        original.setTotalHours(3);
        original.setStyle("detailed");
        original.setWeakPointIds(List.of(21));

        String json = om.writeValueAsString(original);
        PythonApiClient.LessonPrepRequest deserialized =
                om.readValue(json, PythonApiClient.LessonPrepRequest.class);

        assertEquals(original.getKnowledgePointIds(), deserialized.getKnowledgePointIds());
        assertEquals(original.getKnowledgePointNames(), deserialized.getKnowledgePointNames());
        assertEquals(original.getTitle(), deserialized.getTitle());
        assertEquals(original.getStyle(), deserialized.getStyle());
    }

    // =========================================================================
    //  Map 转换测试：验证 buildExtraSnakeMap 的等价行为
    // =========================================================================

    @Test
    @DisplayName("LessonPrepRequest 转 Map 后包含 knowledge_point_names（snake_case key）")
    void lessonPrepRequest_toMap_containsSnakeCaseKey() {
        PythonApiClient.LessonPrepRequest request = new PythonApiClient.LessonPrepRequest();
        request.setUserId(1001);
        request.setKnowledgePointIds(List.of(20, 21));
        request.setKnowledgePointNames(List.of("勾股定理", "逆定理"));
        request.setTeachingGoals(List.of());
        request.setTotalHours(1);
        request.setStyle("standard");
        request.setWeakPointIds(List.of());
        request.setTitle("test");
        request.setSubject("math");
        request.setGrade("grade_8");

        @SuppressWarnings("unchecked")
        Map<String, Object> map = om.convertValue(request, Map.class);

        // 验证 map 中包含 snake_case 的 key
        assertTrue(map.containsKey("knowledge_point_names"),
                "Map 应包含 knowledge_point_names key");
        assertEquals(List.of("勾股定理", "逆定理"), map.get("knowledge_point_names"));
        assertFalse(map.containsKey("knowledgePointNames"),
                "Map 不应包含 camelCase 的 knowledgePointNames key");
    }

    // =========================================================================
    //  跨语言一致性测试：Java JSON 模拟 Python 解析
    // =========================================================================

    @Test
    @DisplayName("Java 序列化的 JSON 可被 Python LessonPrepInternalRequest 解析（字段对齐）")
    void javaJson_fieldsAlignWithPythonRequest() throws Exception {
        PythonApiClient.LessonPrepRequest request = new PythonApiClient.LessonPrepRequest();
        request.setUserId(1001);
        request.setTitle("勾股定理");
        request.setSubject("math");
        request.setGrade("grade_8");
        request.setKnowledgePointIds(List.of(20, 21, 22));
        request.setKnowledgePointNames(List.of("勾股定理", "勾股定理的逆定理", "勾股数"));
        request.setTeachingGoals(List.of("理解勾股定理"));
        request.setTotalHours(1);
        request.setStyle("standard");
        request.setWeakPointIds(List.of());

        String json = om.writeValueAsString(request);

        @SuppressWarnings("unchecked")
        Map<String, Object> fields = om.readValue(json, Map.class);

        // 验证所有 Python LessonPrepInternalRequest 期望的 snake_case 字段都存在
        assertEquals(1001, fields.get("user_id"));
        assertEquals("勾股定理", fields.get("title"));
        assertEquals("math", fields.get("subject"));
        assertEquals("grade_8", fields.get("grade"));
        assertEquals(List.of(20, 21, 22), fields.get("knowledge_point_ids"));
        assertEquals(List.of("勾股定理", "勾股定理的逆定理", "勾股数"), fields.get("knowledge_point_names"));
        assertEquals(List.of("理解勾股定理"), fields.get("teaching_goals"));
        assertEquals(1, fields.get("total_hours"));
        assertEquals("standard", fields.get("style"));
    }
}
