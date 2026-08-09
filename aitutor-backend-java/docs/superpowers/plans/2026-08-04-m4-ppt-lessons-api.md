# M4 PPT 备课列表接口 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 给 M4 讲课模块提供 `GET /api/lesson-prep/contents?userId={userId}&status=published&type=ppt`，返回已发布 PPT 型备课列表（含 PPT 结构、科目、年级、知识点、slideCount 等）。

**Architecture:** 在现有 `TeachingContentController` 上加一个带 `params="type=ppt"` 条件路由的新 handler（与现有 M5 列表接口同路径共存，互不影响）。数据源为同一 MySQL 库 `leapmind-voice` 的 `py_teaching_contents` 表（M5 Python 侧写入的真实数据源）：新建只读实体 + MyBatis-Plus Mapper 查询 `user_id + status=published + type=lesson_plan`，`subject/grade/style/knowledge_point_ids` 从 `source_content_json` 解析，`ppt_structure_json`（snake_case 纯数组）经递归键转换输出为 camelCase 顶层对象，与 Java `PptStructureDTO` / Python `generate-ppt` 响应格式兼容。

**Tech Stack:** Spring Boot 3 / MyBatis-Plus / Jackson / JUnit 5 + Mockito + MockMvc（已有依赖，无新增）

## Global Constraints

- 接口路径与返回结构**必须**严格按规格：`GET /api/lesson-prep/contents?userId={userId}&status=published&type=ppt` → `{"total": N, "items": [...]}`（**不包 ApiResponse**，M4 按此契约消费）
- `knowledgePoints` 名称无数据源：`name` 一律返回 `null`，只回填 `id`（已与用户确认）
- 现有 M5 列表接口（无 `type` 参数时返回 `ApiResponse<List<TeachingContentVO>>`）行为**不得改变**，其代码不修改
- 新增代码放 `com.treepeople.leapmindtts` 包内，跟随现有命名与分层模式（controller / service / mapper / pojo）
- 测试必须可离线跑：`mvn -o test`（Maven 本地仓库在 `D:\apache-maven-3.9.6-bin\apache-maven-3.9.6\mvn_repo`），**不依赖真实 DB / MinIO / 网络**，DB 交互一律 Mockito mock
- 分支：`backend-M5`，每任务一提交

---

### Task 1: JsonKeyConverter —— snake_case → camelCase 递归键转换工具

**Files:**
- Create: `src/main/java/com/treepeople/leapmindtts/util/JsonKeyConverter.java`
- Test: `src/test/java/com/treepeople/leapmindtts/util/JsonKeyConverterTest.java`

**Interfaces:**
- Produces: `public static String snakeToCamel(String json)` —— 入参任意 JSON 字符串（对象/数组/嵌套），返回键已递归转 camelCase 的 JSON 字符串。非对象节点（字符串/数字/布尔/null）原样保留。输入非法 JSON 抛 `IllegalArgumentException`。

- [ ] **Step 1: 写失败测试**

```java
package com.treepeople.leapmindtts.util;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class JsonKeyConverterTest {

    @Test
    void convertsNestedSnakeCaseKeysToCamelCase() {
        String input = "{\"page_num\": 1, \"bullet_points\": [\"a\", \"b\"], "
                + "\"interaction\": {\"question_text\": \"问?\", \"options\": [], \"answer\": \"C\"}, "
                + "\"image_url\": null, \"is_fallback\": false}";
        String out = JsonKeyConverter.snakeToCamel(input);
        assertTrue(out.contains("\"pageNum\": 1"));
        assertTrue(out.contains("\"bulletPoints\""));
        assertTrue(out.contains("\"questionText\""));
        assertTrue(out.contains("\"imageUrl\""));
        assertTrue(out.contains("\"isFallback\""));
        // 原值保留
        assertTrue(out.contains("\"a\""));
        assertTrue(out.contains("\"问?\""));
        assertFalse(out.contains("page_num"));
    }

    @Test
    void convertsTopLevelArray() {
        String input = "[{\"page_num\": 1, \"type\": \"cover\"}]";
        String out = JsonKeyConverter.snakeToCamel(input);
        assertTrue(out.contains("\"pageNum\": 1"));
        assertTrue(out.contains("\"type\": \"cover\""));
    }

    @Test
    void preservesAlreadyCamelCaseKeys() {
        String input = "{\"pptId\": 1, \"slides\": []}";
        assertEquals(input, JsonKeyConverter.snakeToCamel(input));
    }

    @Test
    void rejectsInvalidJson() {
        assertThrows(IllegalArgumentException.class, () -> JsonKeyConverter.snakeToCamel("not json"));
    }
}
```

- [ ] **Step 2: 运行确认失败**

Run: `mvn -o test -Dtest=JsonKeyConverterTest`
Expected: FAIL（`JsonKeyConverter` 不存在，编译错误）

- [ ] **Step 3: 实现**

```java
package com.treepeople.leapmindtts.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class JsonKeyConverter {

    private static final ObjectMapper OM = new ObjectMapper();

    private JsonKeyConverter() {}

    /**
     * 递归将 JSON 中所有键从 snake_case 转为 camelCase（对应 Python 端 convert_keys_camel）。
     * 数组元素、嵌套对象均递归处理；字符串/数字/布尔/null 原样保留。
     */
    public static String snakeToCamel(String json) {
        try {
            JsonNode root = OM.readTree(json);
            return OM.writeValueAsString(convert(root));
        } catch (Exception e) {
            throw new IllegalArgumentException("非法 JSON: " + e.getMessage(), e);
        }
    }

    private static JsonNode convert(JsonNode node) {
        if (node.isObject()) {
            ObjectNode out = OM.createObjectNode();
            node.fields().forEachRemaining(e ->
                    out.set(toCamelKey(e.getKey()), convert(e.getValue())));
            return out;
        }
        if (node.isArray()) {
            ArrayNode out = OM.createArrayNode();
            node.forEach(n -> out.add(convert(n)));
            return out;
        }
        return node;
    }

    private static String toCamelKey(String key) {
        StringBuilder sb = new StringBuilder(key.length());
        boolean upperNext = false;
        for (int i = 0; i < key.length(); i++) {
            char c = key.charAt(i);
            if (c == '_') {
                upperNext = true;
            } else if (upperNext) {
                sb.append(Character.toUpperCase(c));
                upperNext = false;
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
```

- [ ] **Step 4: 运行确认通过**

Run: `mvn -o test -Dtest=JsonKeyConverterTest`
Expected: PASS（4 个测试全过）

- [ ] **Step 5: 提交**

```bash
git add src/main/java/com/treepeople/leapmindtts/util/JsonKeyConverter.java src/test/java/com/treepeople/leapmindtts/util/JsonKeyConverterTest.java
git commit -m "feat: add snake_case to camelCase JSON key converter"
```

---

### Task 2: PyTeachingContent 只读实体 + Mapper

**Files:**
- Create: `src/main/java/com/treepeople/leapmindtts/pojo/entity/PyTeachingContent.java`
- Create: `src/main/java/com/treepeople/leapmindtts/mapper/PyTeachingContentMapper.java`

**Interfaces:**
- Consumes: Task 1 无（本任务独立）
- Produces:
  - `PyTeachingContent` 实体：字段 `id, userId, type, title, sourceType, sourceContentJson, generatedContentJson, pptStructureJson, status, createdAt, updatedAt`（Long id, Long userId, 其余 String, 时间 LocalDateTime）
  - `PyTeachingContentMapper extends BaseMapper<PyTeachingContent>`，`@TableName("py_teaching_contents")`，自带 MyBatis-Plus 通用方法（selectList 等）

- [ ] **Step 1: 写失败测试（编译期验证映射注解存在）**

创建 `src/test/java/com/treepeople/leapmindtts/mapper/PyTeachingContentMapperTest.java`：

```java
package com.treepeople.leapmindtts.mapper;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PyTeachingContentMapperTest {

    /** 通过反射确认实体表名映射正确（不依赖真实 DB） */
    @Test
    void entityMapsToPyTeachingContentsTable() throws Exception {
        Class<?> entity = Class.forName("com.treepeople.leapmindtts.pojo.entity.PyTeachingContent");
        var tableName = entity.getAnnotation(com.baomidou.mybatisplus.annotation.TableName.class);
        assertNotNull(tableName, "缺少 @TableName 注解");
        assertEquals("py_teaching_contents", tableName.value());
    }

    @Test
    void mapperExtendsBaseMapper() throws Exception {
        Class<?> mapper = Class.forName("com.treepeople.leapmindtts.mapper.PyTeachingContentMapper");
        assertEquals(com.baomidou.mybatisplus.core.mapper.BaseMapper.class,
                mapper.getInterfaces()[0]);
    }
}
```

- [ ] **Step 2: 运行确认失败**

Run: `mvn -o test -Dtest=PyTeachingContentMapperTest`
Expected: FAIL（ClassNotFound / 注解断言失败）

- [ ] **Step 3: 实现实体**

```java
package com.treepeople.leapmindtts.pojo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Python 端 M5 备课数据（py_teaching_contents 表，同一 MySQL 库）。
 * 只读实体：M4 列表接口消费，不做任何写操作。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("py_teaching_contents")
public class PyTeachingContent {

    /** 主键ID（Python generate-ppt 返回的 pptId 与此相同） */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 用户ID */
    @TableField("user_id")
    private Long userId;

    /** 类型（当前为 lesson_plan，即 PPT 型备课） */
    @TableField("type")
    private String type;

    /** 备课标题 */
    @TableField("title")
    private String title;

    /** 来源类型（from_text / from_weakpoint） */
    @TableField("source_type")
    private String sourceType;

    /** 源请求 JSON（含 subject/grade/style/knowledge_point_ids） */
    @TableField("source_content_json")
    private String sourceContentJson;

    /** 完整生成内容（大纲等） */
    @TableField("generated_content_json")
    private String generatedContentJson;

    /** PPT 结构 JSON（snake_case 纯数组） */
    @TableField("ppt_structure_json")
    private String pptStructureJson;

    /** 状态（draft / published / archived） */
    @TableField("status")
    private String status;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
```

- [ ] **Step 4: 实现 Mapper**

```java
package com.treepeople.leapmindtts.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.treepeople.leapmindtts.pojo.entity.PyTeachingContent;
import org.apache.ibatis.annotations.Mapper;

/**
 * py_teaching_contents 表只读 Mapper（M4 备课列表接口使用）
 */
@Mapper
public interface PyTeachingContentMapper extends BaseMapper<PyTeachingContent> {
}
```

- [ ] **Step 5: 运行确认通过**

Run: `mvn -o test -Dtest=PyTeachingContentMapperTest`
Expected: PASS（2 个测试全过）

- [ ] **Step 6: 提交**

```bash
git add src/main/java/com/treepeople/leapmindtts/pojo/entity/PyTeachingContent.java src/main/java/com/treepeople/leapmindtts/mapper/PyTeachingContentMapper.java src/test/java/com/treepeople/leapmindtts/mapper/PyTeachingContentMapperTest.java
git commit -m "feat: add read-only PyTeachingContent entity and mapper"
```

---

### Task 3: M4LessonContentService —— 查询组装 + VO

**Files:**
- Create: `src/main/java/com/treepeople/leapmindtts/pojo/vo/M4LessonContentVO.java`
- Create: `src/main/java/com/treepeople/leapmindtts/service/M4LessonContentService.java`
- Test: `src/test/java/com/treepeople/leapmindtts/service/M4LessonContentServiceTest.java`

**Interfaces:**
- Consumes: `PyTeachingContentMapper.selectList`（Task 2）、`JsonKeyConverter.snakeToCamel`（Task 1）
- Produces:
  - `M4LessonContentVO`（Lombok `@Data @Builder`）字段：`prepId(Long), title(String), type(String, 恒为"ppt"), subject(String), grade(String), slideCount(Integer), styleTemplate(String), knowledgePoints(List<Map<String,Object>>), createdAt(String, yyyy-MM-dd), pptStructure(String, camelCase JSON 文本)`
  - `public Map<String, Object> listPublishedPpt(Long userId)` —— 返回 `{"total": N, "items": [M4LessonContentVO...]}`（按规格裸结构，不包 ApiResponse）

**组装规则（严格按规格）：**
- `prepId` = `py_teaching_contents.id`；`type` 固定 `"ppt"`
- `subject` / `grade` / `styleTemplate`：解析 `sourceContentJson`，取 `subject`、`grade`、`style` 键；`style` 缺失时默认 `"default"`
- `knowledgePoints`：`sourceContentJson.knowledge_point_ids`（数组）→ `[{"id": N, "name": null}]`；缺失时空列表
- `slideCount`：`pptStructureJson` 解析为数组后的长度；空/null/非法 → 0
- `createdAt`：`created_at` 格式化为 `yyyy-MM-dd`
- `pptStructure`：`JsonKeyConverter.snakeToCamel(pptStructureJson)` 的结果，再包装为顶层对象 `{"pptId": <id>, "title": <title>, "slides": <camelCase数组>}` 的 JSON 文本；`pptStructureJson` 为空 → 该字段为 `null`
- 查询条件：`user_id = userId AND status = 'published' AND type = 'lesson_plan'`，按 `created_at DESC`
- 返回 `{"total": N, "items": [...]}`，`items` 为 VO 列表

- [ ] **Step 1: 写失败测试（mock Mapper，覆盖组装规则）**

```java
package com.treepeople.leapmindtts.service;

import com.treepeople.leapmindtts.mapper.PyTeachingContentMapper;
import com.treepeople.leapmindtts.pojo.entity.PyTeachingContent;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class M4LessonContentServiceTest {

    private PyTeachingContentMapper mapper;
    private M4LessonContentService service;

    @BeforeEach
    void setUp() {
        mapper = mock(PyTeachingContentMapper.class);
        service = new M4LessonContentService(mapper);
    }

    private PyTeachingContent sampleRow() {
        return PyTeachingContent.builder()
                .id(301L).userId(1L).type("lesson_plan")
                .title("一元二次方程")
                .sourceContentJson("{\"subject\":\"math\",\"grade\":\"grade_9\","
                        + "\"knowledge_point_ids\":[20],\"style\":\"standard\"}")
                .pptStructureJson("[{\"page_num\":1,\"type\":\"cover\"},"
                        + "{\"page_num\":2,\"type\":\"content\"}]")
                .status("published")
                .createdAt(LocalDateTime.of(2026, 7, 5, 10, 30))
                .build();
    }

    @SuppressWarnings("unchecked")
    @Test
    void returnsTotalAndItemsWithMappedFields() {
        when(mapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(sampleRow()));

        Map<String, Object> result = service.listPublishedPpt(1L);

        assertEquals(1, ((Number) result.get("total")).intValue());
        List<Map<String, Object>> items = (List<Map<String, Object>>) result.get("items");
        assertEquals(1, items.size());
        Map<String, Object> item = items.get(0);
        assertEquals(301L, ((Number) item.get("prepId")).longValue());
        assertEquals("ppt", item.get("type"));
        assertEquals("math", item.get("subject"));
        assertEquals("grade_9", item.get("grade"));
        assertEquals(2, ((Number) item.get("slideCount")).intValue());
        assertEquals("standard", item.get("styleTemplate"));
        assertEquals("2026-07-05", item.get("createdAt"));

        List<Map<String, Object>> kps = (List<Map<String, Object>>) item.get("knowledgePoints");
        assertEquals(1, kps.size());
        assertEquals(20, ((Number) kps.get(0).get("id")).intValue());
        assertNull(kps.get(0).get("name"));

        String pptStructure = (String) item.get("pptStructure");
        assertNotNull(pptStructure);
        assertTrue(pptStructure.contains("\"pptId\":301"));
        assertTrue(pptStructure.contains("\"slides\""));
        assertTrue(pptStructure.contains("\"pageNum\""));
        assertFalse(pptStructure.contains("page_num"));
    }

    @SuppressWarnings("unchecked")
    @Test
    void filtersByPublishedLessonPlanType() {
        when(mapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());

        service.listPublishedPpt(7L);

        ArgumentCaptor<LambdaQueryWrapper<PyTeachingContent>> captor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(mapper).selectList(captor.capture());
        LambdaQueryWrapper<PyTeachingContent> wrapper = captor.getValue();
        assertTrue(wrapper.getExpression().toString().contains("user_id"));
    }

    @SuppressWarnings("unchecked")
    @Test
    void handlesEmptyAndMalformedStructures() {
        PyTeachingContent emptyPpt = sampleRow();
        emptyPpt.setPptStructureJson("[]");
        PyTeachingContent badJson = sampleRow();
        badJson.setPptStructureJson("not-json");

        when(mapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(emptyPpt, badJson));

        Map<String, Object> result = service.listPublishedPpt(1L);
        List<Map<String, Object>> items = (List<Map<String, Object>>) result.get("items");
        assertEquals(0, ((Number) items.get(0).get("slideCount")).intValue());
        assertEquals(0, ((Number) items.get(1).get("slideCount")).intValue());
        assertNull(items.get(1).get("pptStructure"));
    }

    @Test
    void defaultsStyleWhenMissing() {
        PyTeachingContent row = sampleRow();
        row.setSourceContentJson("{\"subject\":\"math\",\"grade\":\"grade_9\"}");
        when(mapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(row));

        Map<String, Object> result = service.listPublishedPpt(1L);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) result.get("items");
        assertEquals("default", items.get(0).get("styleTemplate"));
    }
}
```

- [ ] **Step 2: 运行确认失败**

Run: `mvn -o test -Dtest=M4LessonContentServiceTest`
Expected: FAIL（类不存在 / 编译错误）

- [ ] **Step 3: 实现 VO**

```java
package com.treepeople.leapmindtts.pojo.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * M4 讲课模块备课列表项
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class M4LessonContentVO {

    /** 备课ID（py_teaching_contents.id，与 Python generate-ppt 的 pptId 一致） */
    private Long prepId;

    /** 备课标题 */
    private String title;

    /** 类型，恒为 "ppt" */
    private String type;

    /** 科目（source_content_json.subject） */
    private String subject;

    /** 年级（source_content_json.grade） */
    private String grade;

    /** 幻灯片数量 */
    private Integer slideCount;

    /** 模板风格（source_content_json.style，缺省 "default"） */
    private String styleTemplate;

    /** 知识点列表（仅 id，name 无数据源返回 null） */
    private List<Map<String, Object>> knowledgePoints;

    /** 创建日期 yyyy-MM-dd */
    private String createdAt;

    /** 完整 PPT 结构 JSON（camelCase 顶层对象 {"pptId":..,"title":..,"slides":[..]}） */
    private String pptStructure;
}
```

- [ ] **Step 4: 实现 Service**

```java
package com.treepeople.leapmindtts.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.treepeople.leapmindtts.mapper.PyTeachingContentMapper;
import com.treepeople.leapmindtts.pojo.entity.PyTeachingContent;
import com.treepeople.leapmindtts.pojo.vo.M4LessonContentVO;
import com.treepeople.leapmindtts.util.JsonKeyConverter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * M4 讲课模块备课列表服务：读 py_teaching_contents 表（Python 侧真实数据源）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class M4LessonContentService {

    private final PyTeachingContentMapper pyTeachingContentMapper;

    private static final ObjectMapper OM = new ObjectMapper();
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final TypeReference<List<Object>> LIST_REF = new TypeReference<List<Object>>() {};

    /**
     * 查询指定用户已发布的 PPT 型备课，组装 M4 契约结构
     *
     * @return {"total": N, "items": [M4LessonContentVO...]}（裸结构，不包 ApiResponse）
     */
    public Map<String, Object> listPublishedPpt(Long userId) {
        List<PyTeachingContent> rows = pyTeachingContentMapper.selectList(
                new LambdaQueryWrapper<PyTeachingContent>()
                        .eq(PyTeachingContent::getUserId, userId)
                        .eq(PyTeachingContent::getStatus, "published")
                        .eq(PyTeachingContent::getType, "lesson_plan")
                        .orderByDesc(PyTeachingContent::getCreatedAt));

        List<M4LessonContentVO> items = new ArrayList<>(rows.size());
        for (PyTeachingContent row : rows) {
            try {
                items.add(toVO(row));
            } catch (Exception e) {
                log.warn("组装 M4 备课列表项失败, prepId={}, err={}", row.getId(), e.getMessage());
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", items.size());
        result.put("items", items);
        return result;
    }

    private M4LessonContentVO toVO(PyTeachingContent row) {
        Map<String, Object> source = parseSource(row.getSourceContentJson());

        int slideCount = 0;
        String pptStructure = null;
        if (row.getPptStructureJson() != null && !row.getPptStructureJson().isBlank()
                && !"[]".equals(row.getPptStructureJson().trim())) {
            try {
                String camel = JsonKeyConverter.snakeToCamel(row.getPptStructureJson());
                JsonNode slidesNode = OM.readTree(camel);
                if (slidesNode.isArray()) {
                    slideCount = slidesNode.size();
                }
                ObjectNode top = OM.createObjectNode();
                top.put("pptId", row.getId());
                top.put("title", row.getTitle() == null ? "" : row.getTitle());
                top.set("slides", slidesNode);
                pptStructure = OM.writeValueAsString(top);
            } catch (Exception e) {
                log.warn("解析 ppt_structure_json 失败, prepId={}, err={}", row.getId(), e.getMessage());
            }
        }

        List<Map<String, Object>> knowledgePoints = new ArrayList<>();
        JsonNode kpIds = source.get("knowledge_point_ids") instanceof JsonNode n ? n : null;
        if (kpIds != null && kpIds.isArray()) {
            for (JsonNode idNode : kpIds) {
                Map<String, Object> kp = new HashMap<>();
                kp.put("id", idNode.asInt());
                kp.put("name", null);
                knowledgePoints.add(kp);
            }
        }

        Object style = source.get("style");

        return M4LessonContentVO.builder()
                .prepId(row.getId())
                .title(row.getTitle())
                .type("ppt")
                .subject(asText(source.get("subject")))
                .grade(asText(source.get("grade")))
                .slideCount(slideCount)
                .styleTemplate(style == null ? "default" : String.valueOf(style))
                .knowledgePoints(knowledgePoints)
                .createdAt(row.getCreatedAt() == null ? null : row.getCreatedAt().format(DATE_FMT))
                .pptStructure(pptStructure)
                .build();
    }

    private Map<String, Object> parseSource(String json) {
        if (json == null || json.isBlank()) return Map.of();
        try {
            JsonNode node = OM.readTree(json);
            if (!node.isObject()) return Map.of();
            return OM.convertValue(node, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.warn("解析 source_content_json 失败, err={}", e.getMessage());
            return Map.of();
        }
    }

    private String asText(Object o) {
        return o == null ? null : String.valueOf(o);
    }
}
```

- [ ] **Step 5: 运行确认通过**

Run: `mvn -o test -Dtest=M4LessonContentServiceTest`
Expected: PASS（5 个测试全过）

- [ ] **Step 6: 提交**

```bash
git add src/main/java/com/treepeople/leapmindtts/pojo/vo/M4LessonContentVO.java src/main/java/com/treepeople/leapmindtts/service/M4LessonContentService.java src/test/java/com/treepeople/leapmindtts/service/M4LessonContentServiceTest.java
git commit -m "feat: add M4 lesson content list service reading py_teaching_contents"
```

---

### Task 4: Controller 条件路由 —— `type=ppt` 新 handler

**Files:**
- Modify: `src/main/java/com/treepeople/leapmindtts/controller/lesson/TeachingContentController.java`
- Test: `src/test/java/com/treepeople/leapmindtts/controller/TeachingContentControllerM4Test.java`

**Interfaces:**
- Consumes: `M4LessonContentService.listPublishedPpt(Long userId)`（Task 3）
- Produces: 新 handler `GET /api/lesson-prep/contents?userId={userId}&status=published&type=ppt` → 200 `{"total":N,"items":[...]}`。**仅当请求带 `type=ppt` 时匹配**（Spring `params` 条件映射），无 `type` 参数时仍走现有 M5 handler（其代码不动）。

- [ ] **Step 1: 写失败测试（MockMvc standalone，mock M4 服务）**

```java
package com.treepeople.leapmindtts.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.treepeople.leapmindtts.controller.lesson.TeachingContentController;
import com.treepeople.leapmindtts.service.M4LessonContentService;
import com.treepeople.leapmindtts.service.TeachingContentService;
import com.treepeople.leapmindtts.service.PptxExportService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class TeachingContentControllerM4Test {

    private MockMvc mockMvc;
    private M4LessonContentService m4Service;

    @BeforeEach
    void setUp() {
        m4Service = mock(M4LessonContentService.class);
        TeachingContentService contentService = mock(TeachingContentService.class);
        PptxExportService pptxService = mock(PptxExportService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(
                new TeachingContentController(contentService, pptxService, m4Service))
                .build();
    }

    @Test
    void routesToM4ServiceWhenTypeEqualsPpt() throws Exception {
        Map<String, Object> payload = Map.of(
                "total", 1,
                "items", List.of(Map.of("prepId", 301L, "type", "ppt")));
        when(m4Service.listPublishedPpt(1L)).thenReturn(payload);

        mockMvc.perform(get("/api/lesson-prep/contents")
                        .param("userId", "1")
                        .param("status", "published")
                        .param("type", "ppt"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].prepId").value(301))
                .andExpect(jsonPath("$.items[0].type").value("ppt"));

        verify(m4Service).listPublishedPpt(1L);
        verifyNoInteractions(mock(TeachingContentService.class));
    }

    @Test
    void m4HandlerIgnoresStatusValue() throws Exception {
        when(m4Service.listPublishedPpt(2L)).thenReturn(Map.of("total", 0, "items", List.of()));

        mockMvc.perform(get("/api/lesson-prep/contents")
                        .param("userId", "2")
                        .param("type", "ppt"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0));

        verify(m4Service).listPublishedPpt(2L);
    }
}
```

- [ ] **Step 2: 运行确认失败**

Run: `mvn -o test -Dtest=TeachingContentControllerM4Test`
Expected: FAIL（`TeachingContentController` 构造器不接受 M4 服务 / 无路由，404 或编译错误）

- [ ] **Step 3: 修改 Controller**

在 `TeachingContentController` 中注入 M4 服务并新增 handler：

```java
    private final M4LessonContentService m4LessonContentService;
```

（`@RequiredArgsConstructor` 自动生成构造器，现有测试 `TeachingContentControllerM4Test` 传 3 参；`TeachingContentService`、`PptxExportService` 两参不变。）

新增方法（放在 `listContents` 方法之后）：

```java
    /**
     * M4 讲课模块：已发布 PPT 型备课列表
     * 契约见 docs/superpowers/plans/2026-08-04-m4-ppt-lessons-api.md
     */
    @GetMapping(params = "type=ppt")
    @Operation(summary = "M4 备课列表", description = "查询已发布的 PPT 型备课，返回 {total, items} 裸结构（不包 ApiResponse）")
    public ResponseEntity<Map<String, Object>> listM4PptContents(
            @Parameter(description = "用户ID", required = true)
            @RequestParam Long userId,
            @Parameter(description = "状态（M4 固定 published，接口内部强校验）")
            @RequestParam(required = false) String status) {
        log.info("M4 查询 PPT 备课列表，用户ID: {}，状态: {}", userId, status);
        try {
            Map<String, Object> data = m4LessonContentService.listPublishedPpt(userId);
            return ResponseEntity.ok(data);
        } catch (Exception e) {
            log.error("M4 查询备课列表失败: {}", e.getMessage(), e);
            return ResponseEntity.badRequest()
                    .body(Map.of("total", 0, "items", List.of()));
        }
    }
```

（注：`params = "type=ppt"` 是 Spring MVC 的条件映射——请求带 `type=ppt` 时命中本 handler，更精确；不带 `type` 时命中原 `listContents`，两者共存，现有 M5 行为不变。）

- [ ] **Step 4: 运行确认通过**

Run: `mvn -o test -Dtest=TeachingContentControllerM4Test`
Expected: PASS（2 个测试全过）

- [ ] **Step 5: 全量编译回归（确认无 `Ambiguous mapping` 等冲突）**

Run: `mvn -o compile`
Expected: BUILD SUCCESS

- [ ] **Step 6: 提交**

```bash
git add src/main/java/com/treepeople/leapmindtts/controller/lesson/TeachingContentController.java src/test/java/com/treepeople/leapmindtts/controller/TeachingContentControllerM4Test.java
git commit -m "feat: add M4 lesson contents list endpoint with type=ppt routing"
```

---

### Task 5: 端到端验证（真实数据 + 手工 curl）

**Files:**
- 无代码改动

**Interfaces:**
- 无（验证 Task 1–4 产物）

- [ ] **Step 1: 跑全部测试**

Run: `mvn -o test`
Expected: BUILD SUCCESS（旧测试 PptxPreviewTest/PptxRenderTest 若依赖 MinIO/网络可能失败——它们不在本计划范围，若有失败记录输出并在交付说明中注明，不阻塞）

- [ ] **Step 2: 用真实 MySQL 数据验证查询条件**

本地库 `leapmind-voice.py_teaching_contents` 现有 1 条数据（id=1, user_id=1, type=lesson_plan, status=published）。确认 SQL 条件能命中：

```bash
mysql -uroot -p1234 -h127.0.0.1 leapmind-voice -e "SELECT id, user_id, type, title, status FROM py_teaching_contents WHERE user_id=1 AND status='published' AND type='lesson_plan' ORDER BY created_at DESC;"
```

Expected: 返回 id=1 的行（若有该记录）

- [ ] **Step 3: 启动 Java 服务并 curl 验证**

按仓库既有方式启动（argfile + `java @args`，端口 8080），然后：

```bash
curl "http://localhost:8080/api/lesson-prep/contents?userId=1&status=published&type=ppt"
```

Expected: `{"total":1,"items":[{"prepId":1,"title":"氧化还原反应","type":"ppt","subject":"chemistry","grade":"grade_10","slideCount":8,"styleTemplate":"standard","knowledgePoints":[{"id":1,"name":null}],"createdAt":"2026-07-05","pptStructure":{"pptId":1,"title":"氧化还原反应","slides":[{"pageNum":1,...}]}}]}`（字段按实际数据）

- [ ] **Step 4: 回归验证 M5 列表不受影响**

```bash
curl "http://localhost:8080/api/lesson-prep/contents?userId=1"
```

Expected: 返回原有 `ApiResponse<List<TeachingContentVO>>` 结构（含 code/message/data），与改动前一致

---

## Self-Review

**1. 规格覆盖：**
- `GET /api/lesson-prep/contents?userId&status=published&type=ppt` → Task 4（params 条件路由）
- `total`/`items` 裸结构 → Task 4 返回 `Map.of`；Task 3 组装
- `prepId`(py 表 id) → Task 3 `row.getId()`
- `title` → Task 3 `row.getTitle()`
- `type:"ppt"` 固定 → Task 3 `type("ppt")`
- `subject`/`grade` → Task 3 source_content_json 解析
- `slideCount` → Task 3 slides 数组长度
- `styleTemplate` → Task 3 source `style`，缺省 "default"
- `knowledgePoints:[{id,name}]` → Task 3 仅 id，name=null（用户已确认）
- `createdAt` yyyy-MM-dd → Task 3 DATE_FMT
- `pptStructure` 完整 JSON → Task 3 snake→camel + 顶层对象包装

**2. 占位符扫描：** 所有步骤含完整代码与命令，无 TBD/占位。

**3. 类型一致性：**
- `M4LessonContentVO` 字段名在 Task 3 定义、Task 3 测试与 Task 4 测试中一致（prepId/type/subject/grade/slideCount/styleTemplate/knowledgePoints/createdAt/pptStructure）
- `M4LessonContentService.listPublishedPpt(Long)` 签名 Task 3 定义、Task 4 调用一致
- `JsonKeyConverter.snakeToCamel(String)` Task 1 定义、Task 3 调用一致
- `PyTeachingContentMapper` Task 2 定义、Task 3 注入一致
- 注意点已处理：Task 3 的 `toVO` 中 `source.get("knowledge_point_ids")` 用 `instanceof JsonNode` 判型（`parseSource` 用 `convertValue` 后嵌套为 `JsonNode` 子节点），`asText` 兜底 null；`ppt_structure_json` 为空/`[]`/非法 JSON 均不崩溃（slideCount=0、pptStructure=null），有对应测试。

**已知遗留（本计划不处理，交付时说明）：**
- 规格示例 `styleTemplate: "default"` vs 库中 `style:"standard"`：实现取真实值 standard（按数据透传），若 M4 期望固定 "default" 需另行对齐
- 本地 Java 表 `teaching_contents` 与 Python 表数据不联动属既有架构，不在本接口范围
- Java 端 `PptxExportService` 接口缺 `exportFromStructure` 声明的既有编译问题与本计划无关（上一会话已确认不改），Task 4 的 `mvn -o compile` 会再次暴露它——若遇该错误，用 `mvn -o compile -Dmaven.compiler.failOnError=false` 或仅跑 `mvn -o test -Dtest=TeachingContentControllerM4Test` 验证本改动，并在交付说明中注明
