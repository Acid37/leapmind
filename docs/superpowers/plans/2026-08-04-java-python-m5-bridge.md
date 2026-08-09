# Java 调用 Python M5 备课生成管线（方式一）Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Java (Spring Boot 8080) 通过 `POST /api/lesson-prep/generate` 流式调用 Python (FastAPI 8001) 的三阶段备课生成管线，SSE 透传前端，生成完成后数据分别落在 Java `teaching_contents` 与 Python `py_teaching_contents`（同一 MySQL 库 `leapmind-voice`）。

**Architecture:** 前端先通过现有 `GET /api/ppt/sse/{connectionId}` 建立 SSE 连接，再 POST Java 新端点触发生成。Java 后台线程经 WebClient 调 Python `/api/lesson-prep/generate`（SSE 流），逐事件用 `SsePushServiceImpl.sendEvent` 原样透传（Python 的事件本身就是 `data: {"type":...}` JSON，直接转发）。Python `done` 事件携带 py 表 prepId，Java 随后从 `py_teaching_contents` 读回生成结果，映射落库到 `teaching_contents`（新增 `source_prep_id` 列溯源）。Python 端代码零改动，仅确认端口为 8001。

**Tech Stack:** Java 17 / Spring Boot / WebFlux WebClient（已配置）/ MyBatis-Plus / Flyway（V8 迁移）/ JUnit 5 + OkHttp MockWebServer 4.11.0（已在 pom.xml）/ Python FastAPI + uvicorn（不改代码）。

## Global Constraints

- **Python 代码零改动**（方式一的核心承诺），仅验证端口。可选的 `utcnow → now` 时间戳修改必须单独列在任务 6，且默认不做。
- **请求/响应字段 camelCase**，与 Python `LessonPrepRequest` 一致：`userId`、`knowledgePointIds`、`teachingGoals`、`totalHours`、`weakPointIds`、`userProfileSummary`（见 [lesson_prep_api.py:21-35](aitutor-backend-python/src/landppt/api/lesson_prep_api.py)）。
- Python SSE 事件**没有 `event:` 行**，全部在 `data: {"type":"outline","content":{...}}` 的 JSON `type` 字段里（[lesson_prep_service.py:86-93](aitutor-backend-python/src/landppt/services/lesson_prep_service.py)）。事件顺序：`outline` → `section`×N → `slide`×N → `slides_done` → `narration`×N → `done`（含 `prepId`）；异常时 `error`。Java 解析器**必须**按此实现。
- Python API 登录：`POST /api/auth/login`，**form-urlencoded**（username/password），响应 JSON `{success, session_id, user}`，**不设置 Cookie**——Java 调用受保护端点必须手动带 `Cookie: session_id=<值>` 头（[routes.py:184-203](aitutor-backend-python/src/landppt/auth/routes.py)）。默认账号 admin/admin123。
- Java 端口 8080，Python M5 端口 **8001**（run.py 与 config.py 默认值已是 8001；M3 backend 占 8000，不得用 8000 启动 M5）。
- 现有 SSE 模式：前端先 `GET /api/ppt/sse/{connectionId}` 建连，再 POST 触发任务（[PptExportController.java:184-204](aitutor-backend-java/src/main/java/com/treepeople/leapmindtts/controller/lesson/PptExportController.java)）。新端点遵循此模式，**不新建** SSE 连接端点。
- Java 代码风格：Lombok（`@Slf4j`/`@RequiredArgsConstructor`/`@Data`/`@Builder`）、`ApiResponse.success/error`、MyBatis-Plus `IService`/`BaseMapper`。controller 包 `controller.lesson`，service 接口 `service`、实现 `service.impl`。
- 时间戳：Java 表 `created_at/updated_at` 由 MySQL `CURRENT_TIMESTAMP` 管理，无需处理；Java **不读** Python 表的时间戳字段（Python 表为 UTC），避免 8 小时时差问题。
- 配置集中：新增 `lesson-prep.python.*` 配置段（base-url、username、password、timeout），放 application.yml，dev 环境用 application-dev.yml 覆盖（如需要）。
- Java 编译/测试：`mvn -o test`（离线模式，本地仓库 `D:\apache-maven-3.9.6-bin\apache-maven-3.9.6\mvn_repo`）；启动用 argfile 方式（`@argfile`，路径反斜杠转正斜杠），**不要**直接命令行 classpath 启动。
- git 仓库根在 `leapmind/`（不是 D:/leapmind1）。所有 commit 在 `leapmind/` 下执行。

---

### Task 1: Python 端口确认与双服务冒烟

**Files:**
- 只读确认：`aitutor-backend-python/run.py`（默认端口逻辑）、`aitutor-backend-python/src/landppt/core/config.py:236-268`（AppConfig.port 默认 8001）

**Interfaces:**
- Produces: 无代码产出；确认 8001 上 M5 可用、`POST /api/auth/login` 行为符合预期，为 Task 3 的客户端实现提供事实依据。

- [ ] **Step 1: 检查端口来源**

```bash
grep -n 'PORT' aitutor-backend-python/run.py aitutor-backend-python/src/landppt/core/config.py
netstat -ano | grep -E ':(8000|8001)' | grep LISTENING
```

Expected: run.py 和 config.py 默认都是 `8001`；若 8001 已有监听，确认是 M5 进程还是其他程序。若 8000 被 M3 占用属正常，**不要**用 8000 启动 M5。

- [ ] **Step 2: 启动 M5 并冒烟验证**

```bash
cd aitutor-backend-python
PYTHONIOENCODING=utf-8 .venv/Scripts/python.exe run.py
```

Expected: 启动日志显示 `Port: 8001`。若输出 `Port: 8000`，说明环境变量 `PORT=8000` 被设置，用 `PORT=8001` 覆盖启动：

```bash
PORT=8001 PYTHONIOENCODING=utf-8 .venv/Scripts/python.exe run.py
```

（run.py 的 🚀 emoji 在 GBK 控制台会崩，`PYTHONIOENCODING=utf-8` 必带。）

- [ ] **Step 3: 验证 login 端点行为**

```bash
curl -s -X POST http://localhost:8001/api/auth/login -d "username=admin&password=admin123"
```

Expected: `{"success": true, "session_id": "<值>", "user": {...}}`，且响应**没有** `Set-Cookie` 头（可用 `-i` 验证）。记录该 session_id 供 Task 3 联调使用。若 401，确认默认账号或改为真实可用账号（Task 3 中从配置读取）。

- [ ] **Step 4: 冒烟验证 generate 端点可用（可选，耗时 ~16s）**

```bash
curl -s -N -X POST http://localhost:8001/api/lesson-prep/generate \
  -H "Cookie: session_id=<上一步的session_id>" \
  -H "Content-Type: application/json" \
  -d '{"userId":1,"title":"测试","subject":"chemistry","grade":"grade_9","knowledgePointIds":[],"teachingGoals":["理解概念"],"totalHours":1,"style":"standard","weakPointIds":[]}'
```

Expected: 收到 `data: {"type":"outline",...}` → `section`×N → 最终 `data: {"type":"done","prepId":<N>}` 的事件流。确认后 Ctrl+C。这一步确认了事件格式与 Task 2/3 的解析器假设一致。

- [ ] **Step 5: Commit（无代码改动，跳过）**

本任务无代码产出，无 commit。

---

### Task 2: SSE 事件解析器（TDD）

**Files:**
- Create: `aitutor-backend-java/src/main/java/com/treepeople/leapmindtts/integration/SseEventParser.java`
- Test: `aitutor-backend-java/src/test/java/com/treepeople/leapmindtts/integration/SseEventParserTest.java`

**Interfaces:**
- Produces: `SseEventParser.SseEvent` record `(String type, String data)`；`SseEventParser.parse(String eventBlock)` —— 输入一个事件块（多行，行以 `\n` 结尾，不含事件间空行），返回解析结果；非 `data:` 行（注释 `:`、`id:` 等）忽略；多行 `data:` 按 SSE 规范以换行拼接；`type` 取自 JSON 顶层 `type` 字段；`data` 为去掉 `data: ` 前缀后的**原始 JSON 字符串**（供透传）。

- [ ] **Step 1: 写失败测试**

```java
package com.treepeople.leapmindtts.integration;

import com.treepeople.leapmindtts.integration.SseEventParser.SseEvent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SseEventParserTest {

    @Test
    void parsesSingleDataLineEvent() {
        SseEvent e = SseEventParser.parse("data: {\"type\":\"outline\",\"content\":{\"title\":\"x\"}}\n");
        assertEquals("outline", e.type());
        assertEquals("{\"type\":\"outline\",\"content\":{\"title\":\"x\"}}", e.data());
    }

    @Test
    void parsesDoneEventWithPrepId() {
        SseEvent e = SseEventParser.parse("data: {\"type\":\"done\",\"prepId\":301}\n");
        assertEquals("done", e.type());
        assertTrue(e.data().contains("\"prepId\":301"));
    }

    @Test
    void ignoresNonDataLinesAndComments() {
        SseEvent e = SseEventParser.parse(": comment\nid: 42\ndata: {\"type\":\"section\",\"index\":1}\n");
        assertEquals("section", e.type());
    }

    @Test
    void joinsMultiLineData() {
        SseEvent e = SseEventParser.parse("data: {\"type\":\"slide\",\"text\":\"a\n\"data: \"}\n");
        assertEquals("slide", e.type());
    }

    @Test
    void throwsOnBlockWithoutTypeField() {
        assertThrows(IllegalArgumentException.class,
                () -> SseEventParser.parse("data: {\"foo\":1}\n"));
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `cd aitutor-backend-java && mvn -o test -Dtest=SseEventParserTest -pl . 2>&1 | tail -20`（若项目已多模块，省略 `-pl .`）
Expected: 编译失败 `cannot find symbol: SseEventParser`。

- [ ] **Step 3: 实现解析器**

```java
package com.treepeople.leapmindtts.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 解析 Python 端 SSE 事件流中的单个事件块。
 *
 * Python 端不发送标准的 event: 行，事件类型在 data 的 JSON type 字段中：
 *   data: {"type":"outline","content":{...}}
 * 见 aitutor-backend-python/src/landppt/services/lesson_prep_service.py _sse_event()。
 */
public final class SseEventParser {

    private static final ObjectMapper OM = new ObjectMapper();
    private static final String DATA_PREFIX = "data:";

    private SseEventParser() {
    }

    public record SseEvent(String type, String data) {
    }

    public static SseEvent parse(String eventBlock) {
        StringBuilder data = new StringBuilder();
        boolean first = true;
        for (String line : eventBlock.split("\n")) {
            if (line.startsWith(DATA_PREFIX)) {
                String payload = line.substring(DATA_PREFIX.length());
                if (payload.startsWith(" ")) {
                    payload = payload.substring(1);
                }
                if (!first) {
                    data.append('\n');
                }
                data.append(payload);
                first = false;
            }
            // 其他行（注释、id:、空行）忽略
        }
        if (data.length() == 0) {
            throw new IllegalArgumentException("SSE事件块不含 data 行");
        }
        try {
            JsonNode node = OM.readTree(data.toString());
            JsonNode type = node.get("type");
            if (type == null || !type.isTextual()) {
                throw new IllegalArgumentException("data JSON 缺少 type 字段: " + data);
            }
            return new SseEvent(type.asText(), data.toString());
        } catch (java.io.IOException e) {
            throw new IllegalArgumentException("data 不是合法 JSON: " + data, e);
        }
    }
}
```

- [ ] **Step 4: 运行测试确认通过**

Run: `mvn -o test -Dtest=SseEventParserTest`
Expected: 5 个测试全 PASS。

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/treepeople/leapmindtts/integration/SseEventParser.java src/test/java/com/treepeople/leapmindtts/integration/SseEventParserTest.java
git commit -m "feat(m5): 新增 Python SSE 事件解析器 SseEventParser"
```

---

### Task 3: PythonAIClient —— 登录 + 流式调用（TDD）

**Files:**
- Create: `aitutor-backend-java/src/main/java/com/treepeople/leapmindtts/config/PythonPrepProperties.java`
- Create: `aitutor-backend-java/src/main/java/com/treepeople/leapmindtts/integration/PythonAIClient.java`
- Test: `aitutor-backend-java/src/test/java/com/treepeople/leapmindtts/integration/PythonAIClientTest.java`

**Interfaces:**
- Consumes: `SseEventParser.SseEvent`（Task 2）、`WebClientConfig` 的 `webClientBuilder`（已存在，[WebClientConfig.java:22](aitutor-backend-java/src/main/java/com/treepeople/leapmindtts/config/WebClientConfig.java)）。
- Produces: `PythonAIClient.generate(Map<String,Object> requestBody, Consumer<SseEvent> onEvent)`——阻塞直到流结束，按到达顺序回调 `onEvent`（含 outline/section/slide/narration/done/error）；内部先确保 session（`POST /api/auth/login` form 表单，缓存 session_id），请求带 `Cookie: session_id=<值>`；收到 401 时重登并重放一次。`PythonPrepProperties`：`baseUrl`（默认 `http://localhost:8001`）、`username`（默认 `admin`）、`password`（默认 `admin123`）、`timeoutSeconds`（默认 300）。

- [ ] **Step 1: 写失败测试（MockWebServer 模拟 Python 端）**

```java
package com.treepeople.leapmindtts.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.treepeople.leapmindtts.config.PythonPrepProperties;
import com.treepeople.leapmindtts.integration.SseEventParser.SseEvent;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PythonAIClientTest {

    private MockWebServer server;
    private PythonAIClient client;

    @BeforeEach
    void setUp() {
        server = new MockWebServer();
        PythonPrepProperties props = new PythonPrepProperties();
        props.setBaseUrl(server.url("/").toString().replaceAll("/$", ""));
        props.setUsername("admin");
        props.setPassword("admin123");
        WebClient webClient = WebClient.builder()
                .baseUrl(props.getBaseUrl())
                .build();
        client = new PythonAIClient(webClient, props, new ObjectMapper());
    }

    @AfterEach
    void tearDown() throws Exception {
        server.shutdown();
    }

    @Test
    void logsInWithFormAndForwardsSseEvents() throws Exception {
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"success\":true,\"session_id\":\"sid-abc\",\"user\":{\"id\":1}}"));
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "text/event-stream")
                .setBody("data: {\"type\":\"outline\",\"content\":{}}\n\n"
                        + "data: {\"type\":\"section\",\"index\":1}\n\n"
                        + "data: {\"type\":\"done\",\"prepId\":301}\n\n"));

        List<SseEvent> received = new ArrayList<>();
        client.generate(Map.of("userId", 1, "title", "t"), received::add);

        RecordedRequest login = server.takeRequest();
        assertEquals("POST", login.getMethod());
        assertEquals("application/x-www-form-urlencoded", login.getHeader("Content-Type"));
        assertTrue(login.getBody().readUtf8().contains("username=admin"));

        RecordedRequest generate = server.takeRequest();
        assertEquals("sid-abc", generate.getHeader("Cookie").replace("session_id=", ""));
        assertEquals(3, received.size());
        assertEquals("outline", received.get(0).type());
        assertEquals("section", received.get(1).type());
        assertEquals("done", received.get(2).type());
        assertTrue(received.get(2).data().contains("\"prepId\":301"));
    }

    @Test
    void retriesOnceWithFreshSessionOn401() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(200)
                .setBody("{\"success\":true,\"session_id\":\"sid-old\"}"));
        server.enqueue(new MockResponse().setResponseCode(401).setBody("{\"detail\":\"unauthorized\"}"));
        server.enqueue(new MockResponse().setResponseCode(200)
                .setBody("{\"success\":true,\"session_id\":\"sid-new\"}"));
        server.enqueue(new MockResponse().setResponseCode(200)
                .setHeader("Content-Type", "text/event-stream")
                .setBody("data: {\"type\":\"done\",\"prepId\":1}\n\n"));

        List<SseEvent> received = new ArrayList<>();
        client.generate(Map.of("userId", 1), received::add);

        assertEquals(1, received.size());
        assertEquals("done", received.get(0).type());
        // 第二次登录后携带新 session 重放
        server.takeRequest(); // 初次登录
        server.takeRequest(); // 401 的 generate
        server.takeRequest(); // 重登录
        RecordedRequest retry = server.takeRequest();
        assertEquals("sid-new", retry.getHeader("Cookie").replace("session_id=", ""));
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `mvn -o test -Dtest=PythonAIClientTest`
Expected: 编译失败 `cannot find symbol: PythonAIClient / PythonPrepProperties`。

- [ ] **Step 3: 实现配置类与客户端**

```java
package com.treepeople.leapmindtts.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Python 备课服务（FastAPI）连接配置。
 */
@Data
@Component
@ConfigurationProperties(prefix = "lesson-prep.python")
public class PythonPrepProperties {
    /** Python 服务根地址，如 http://localhost:8001 */
    private String baseUrl = "http://localhost:8001";
    /** Python API 登录账号 */
    private String username = "admin";
    /** Python API 登录密码 */
    private String password = "admin123";
    /** 流式调用总超时（秒） */
    private long timeoutSeconds = 300;
}
```

```java
package com.treepeople.leapmindtts.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.treepeople.leapmindtts.config.PythonPrepProperties;
import com.treepeople.leapmindtts.integration.SseEventParser.SseEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.time.Duration;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Python 备课服务客户端：登录获取 session_id 并以 SSE 流式调用生成接口。
 *
 * 已知事实（2026-08 实测）：
 *  - POST /api/auth/login 为 form-urlencoded，响应 JSON 含 session_id，但不设置 Cookie；
 *  - 受保护接口需手动带 Cookie: session_id=<值> 头；
 *  - generate 返回 text/event-stream，事件全部在 data: 行 JSON 的 type 字段中。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PythonAIClient {

    private final WebClient webClient;
    private final PythonPrepProperties props;
    private final ObjectMapper objectMapper;

    private volatile String sessionId;

    /** 登录并缓存 session_id（线程安全：双重检查，失败抛异常）。 */
    public synchronized String ensureSession() {
        if (sessionId != null) {
            return sessionId;
        }
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("username", props.getUsername());
        form.add("password", props.getPassword());
        JsonNode body = webClient.post()
                .uri("/api/auth/login")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .bodyValue(form)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .timeout(Duration.ofSeconds(30))
                .block();
        String sid = body != null && body.hasNonNull("session_id") ? body.get("session_id").asText() : null;
        if (sid == null || sid.isEmpty()) {
            throw new IllegalStateException("Python 登录失败，响应缺少 session_id: " + body);
        }
        sessionId = sid;
        log.info("Python 服务登录成功，session_id: {}", sid);
        return sessionId;
    }

    /**
     * 流式调用生成接口，按到达顺序回调 onEvent（outline/section/slide/narration/done/error）。
     * 401 时重登并重放一次。阻塞直到流结束或超时。
     */
    public void generate(Map<String, Object> requestBody, Consumer<SseEvent> onEvent) {
        try {
            doGenerate(requestBody, onEvent);
        } catch (WebClientResponseException e) {
            if (e.getStatusCode().value() == 401) {
                log.warn("Python session 失效，重新登录后重试一次");
                sessionId = null;
                ensureSession();
                doGenerate(requestBody, onEvent);
            } else {
                throw e;
            }
        }
    }

    private void doGenerate(Map<String, Object> requestBody, Consumer<SseEvent> onEvent) {
        StringBuilder block = new StringBuilder();
        webClient.post()
                .uri("/api/lesson-prep/generate")
                .header(HttpHeaders.COOKIE, "session_id=" + ensureSession())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(requestBody)
                .retrieve()
                .bodyToFlux(String.class)
                .timeout(Duration.ofSeconds(props.getTimeoutSeconds()))
                .doOnError(e -> {
                    if (e instanceof WebClientRequestException && sessionId != null) {
                        log.warn("Python 流中断（连接问题）: {}", e.getMessage());
                    }
                })
                .doOnNext(line -> {
                    if (line.isEmpty()) {
                        if (block.length() > 0) {
                            SseEvent event = SseEventParser.parse(block.toString());
                            onEvent.accept(event);
                            block.setLength(0);
                        }
                    } else {
                        block.append(line).append('\n');
                    }
                })
                .blockLast();
        if (block.length() > 0) {
            // 流末未空行结尾的事件块兜底
            onEvent.accept(SseEventParser.parse(block.toString()));
        }
    }
}
```

> 说明：`WebClientConfig` 提供的 `webClient` bean 已配置 baseUrl 与超时，直接注入 `WebClient` 即可（构造注入处为 `WebClientConfig.webClient` bean）；若测试或实现中需要独立 baseUrl，使用 `WebClient.Builder`。实现采用构造注入 `WebClient`，与 `AIModelService` 现有模式一致。

- [ ] **Step 4: 运行测试确认通过**

Run: `mvn -o test -Dtest=PythonAIClientTest`
Expected: 2 个测试 PASS。

- [ ] **Step 5: 补充 application.yml 配置**

在 `aitutor-backend-java/src/main/resources/application.yml` 末尾追加：

```yaml
lesson-prep:
  python:
    base-url: http://localhost:8001
    username: admin
    password: admin123
    timeout-seconds: 300
```

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/treepeople/leapmindtts/config/PythonPrepProperties.java src/main/java/com/treepeople/leapmindtts/integration/PythonAIClient.java src/test/java/com/treepeople/leapmindtts/integration/PythonAIClientTest.java src/main/resources/application.yml
git commit -m "feat(m5): PythonAIClient 登录与 SSE 流式调用客户端"
```

---

### Task 4: py_teaching_contents 只读实体 + V8 迁移

**Files:**
- Create: `aitutor-backend-java/src/main/java/com/treepeople/leapmindtts/pojo/entity/PyTeachingContent.java`
- Create: `aitutor-backend-java/src/main/java/com/treepeople/leapmindtts/mapper/PyTeachingContentMapper.java`
- Create: `aitutor-backend-java/src/main/resources/db/migration/V8__add_source_prep_id_to_teaching_contents.sql`
- Modify: `aitutor-backend-java/src/main/java/com/treepeople/leapmindtts/pojo/entity/TeachingContent.java`（加 `sourcePrepId` 字段）

**Interfaces:**
- Consumes: 无（独立任务）。
- Produces: `PyTeachingContent`（`@TableName("py_teaching_contents")`，字段：`id(Integer)`、`title(String)`、`generatedContentJson`（列 `generated_content_json`）、`pptStructureJson`（列 `ppt_structure_json`）、`status`、`createdAt`（列 `created_at`，仅映射不读取））；`PyTeachingContentMapper.selectById(Integer id)`（MyBatis-Plus 内置）；`teaching_contents.source_prep_id` 列（BIGINT，NULL 默认）。

- [ ] **Step 1: 写 V8 迁移**

```sql
-- ===============================================
-- V8: teaching_contents 增加 source_prep_id，记录 Python 侧 py_teaching_contents 主键
-- ===============================================
ALTER TABLE teaching_contents
    ADD COLUMN source_prep_id BIGINT DEFAULT NULL COMMENT 'Python侧py_teaching_contents主键ID（溯源用）' AFTER template_id;
```

- [ ] **Step 2: 新建只读实体与 Mapper**

```java
package com.treepeople.leapmindtts.pojo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Python 端 py_teaching_contents 只读映射（同库直接读，永不写入）。
 * 注意：Python 表 created_at/updated_at 为 UTC，本实体不用于时间比较。
 */
@Data
@TableName("py_teaching_contents")
public class PyTeachingContent {

    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    @TableField("title")
    private String title;

    @TableField("generated_content_json")
    private String generatedContentJson;

    @TableField("ppt_structure_json")
    private String pptStructureJson;

    @TableField("status")
    private String status;

    @TableField("created_at")
    private LocalDateTime createdAt;
}
```

```java
package com.treepeople.leapmindtts.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.treepeople.leapmindtts.pojo.entity.PyTeachingContent;
import org.apache.ibatis.annotations.Mapper;

/**
 * Python 端备课内容只读 Mapper（同 MySQL 库跨表读，不做写操作）。
 */
@Mapper
public interface PyTeachingContentMapper extends BaseMapper<PyTeachingContent> {
}
```

- [ ] **Step 3: TeachingContent 实体加字段**

在 `TeachingContent.java` 的 `templateId` 之后、`pptDownloadUrl` 之前插入：

```java
    /**
     * Python侧py_teaching_contents主键ID（溯源）
     */
    @TableField("source_prep_id")
    private Long sourcePrepId;
```

- [ ] **Step 4: 编译 + 验证迁移**

Run: `mvn -o compile`
Expected: BUILD SUCCESS。

验证迁移（启动 Java 后自动执行，或手动执行）：`mvn -o spring-boot:run` 启动后执行：

```bash
mysql -uroot -p1234 leapmind-voice -e "SHOW COLUMNS FROM teaching_contents LIKE 'source_prep_id';"
```

Expected: 返回 `source_prep_id bigint YES NULL` 一行。确认后停止 Java 进程（启动后即停，此任务只验证迁移）。

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/treepeople/leapmindtts/pojo/entity/PyTeachingContent.java src/main/java/com/treepeople/leapmindtts/mapper/PyTeachingContentMapper.java src/main/resources/db/migration/V8__add_source_prep_id_to_teaching_contents.sql src/main/java/com/treepeople/leapmindtts/pojo/entity/TeachingContent.java
git commit -m "feat(m5): py_teaching_contents 只读映射与 source_prep_id 迁移"
```

---

### Task 5: generate 服务与 Controller（TDD）

**Files:**
- Create: `aitutor-backend-java/src/main/java/com/treepeople/leapmindtts/pojo/dto/LessonPrepGenerateRequest.java`
- Create: `aitutor-backend-java/src/main/java/com/treepeople/leapmindtts/service/LessonPrepGenerateService.java`
- Create: `aitutor-backend-java/src/main/java/com/treepeople/leapmindtts/service/impl/LessonPrepGenerateServiceImpl.java`
- Create: `aitutor-backend-java/src/main/java/com/treepeople/leapmindtts/controller/lesson/LessonPrepGenerateController.java`
- Test: `aitutor-backend-java/src/test/java/com/treepeople/leapmindtts/service/impl/LessonPrepGenerateServiceImplTest.java`

**Interfaces:**
- Consumes: `PythonAIClient.generate`（Task 3）、`PyTeachingContentMapper.selectById`（Task 4）、`SsePushServiceImpl.sendEvent/sendComplete/sendError/removeConnection`（已存在）、`TeachingContentService.save/updateById`（MyBatis-Plus IService）、`SseEventParser.SseEvent`。
- Produces: `POST /api/lesson-prep/generate`（body `LessonPrepGenerateRequest`，camelCase）→ `ApiResponse<Map<String,String>>` `{connectionId, message}`；异步流：先透传 Python 事件，`done` 后落库，最后 `complete`；任何异常 → `error` 事件。

**落库规则（关键设计）：**
1. 生成开始前先在 `teaching_contents` 插入 draft 记录，`prep_id = 自身自增 id`（表的主键即备课标识，`selectByPrepId` 用 prep_id 查询的现有模式保持可用）。
2. 收到 Python `done` 事件后，从 data JSON 取 `prepId` → `pyTeachingContentMapper.selectById` → 更新该记录：`title ← py.title`、`status ← "published"`、`pptStructure ← py.ppt_structure_json`、`generatedContentJson ← py.generated_content_json`、`sourcePrepId ← py.id`。
3. 收到 Python `error` 事件 → `sendError`，draft 记录保留（前端可删）。

- [ ] **Step 1: 写失败测试**

```java
package com.treepeople.leapmindtts.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.treepeople.leapmindtts.integration.PythonAIClient;
import com.treepeople.leapmindtts.integration.SseEventParser.SseEvent;
import com.treepeople.leapmindtts.mapper.PyTeachingContentMapper;
import com.treepeople.leapmindtts.pojo.dto.LessonPrepGenerateRequest;
import com.treepeople.leapmindtts.pojo.entity.PyTeachingContent;
import com.treepeople.leapmindtts.pojo.entity.TeachingContent;
import com.treepeople.leapmindtts.service.TeachingContentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LessonPrepGenerateServiceImplTest {

    @Mock
    private PythonAIClient pythonAIClient;
    @Mock
    private PyTeachingContentMapper pyTeachingContentMapper;
    @Mock
    private SsePushServiceImpl ssePushService;
    @Mock
    private TeachingContentService teachingContentService;

    private LessonPrepGenerateServiceImpl service;
    private final ObjectMapper om = new ObjectMapper();

    @BeforeEach
    void setUp() {
        service = new LessonPrepGenerateServiceImpl(
                pythonAIClient, pyTeachingContentMapper, ssePushService, teachingContentService, om);
    }

    @Test
    void persistsPublishedContentAfterDoneEvent() {
        // 1) 初始 draft 记录：save 时生成 id=100
        when(teachingContentService.save(any(TeachingContent.class))).thenAnswer(inv -> {
            TeachingContent tc = inv.getArgument(0);
            tc.setId(100L);
            tc.setPrepId(100L);
            return true;
        });
        // 2) Python 表存在记录 id=301
        PyTeachingContent py = new PyTeachingContent();
        py.setId(301);
        py.setTitle("化学配平");
        py.setStatus("published");
        py.setPptStructureJson("[{\"page_num\":1}]");
        py.setGeneratedContentJson("{\"syllabus\":{\"title\":\"化学配平\"}}");
        when(pyTeachingContentMapper.selectById(301)).thenReturn(py);

        // 3) 捕获 pythonAIClient.generate 回调并模拟 Python 事件序列
        doAnswer(inv -> {
            Consumer<SseEvent> consumer = inv.getArgument(1);
            consumer.accept(new SseEvent("outline", "{\"type\":\"outline\",\"content\":{}}"));
            consumer.accept(new SseEvent("done", "{\"type\":\"done\",\"prepId\":301}"));
            return null;
        }).when(pythonAIClient).generate(anyMap(), any());

        service.generate("conn-1", buildRequest());

        // 4) 校验：done 后更新了 teaching_contents
        ArgumentCaptor<TeachingContent> captor = ArgumentCaptor.forClass(TeachingContent.class);
        verify(teachingContentService).updateById(captor.capture());
        TeachingContent updated = captor.getValue();
        assertEquals("published", updated.getStatus());
        assertEquals("[{\"page_num\":1}]", updated.getPptStructure());
        assertEquals(301L, updated.getSourcePrepId());
        assertEquals("化学配平", updated.getTitle());

        // 5) 校验：完整事件透传 + complete
        verify(ssePushService).sendEvent(eq("conn-1"), eq("outline"), eq("{\"type\":\"outline\",\"content\":{}}"));
        verify(ssePushService).sendComplete(eq("conn-1"), anyMap());
        verify(ssePushService, never()).sendError(anyString(), anyString(), anyString());
    }

    @Test
    void sendsErrorWhenPythonEmitsErrorEvent() {
        when(teachingContentService.save(any(TeachingContent.class))).thenReturn(true);
        doAnswer(inv -> {
            Consumer<SseEvent> consumer = inv.getArgument(1);
            consumer.accept(new SseEvent("error", "{\"type\":\"error\",\"stage\":\"stage1\",\"message\":\"大纲生成失败\"}"));
            return null;
        }).when(pythonAIClient).generate(anyMap(), any());

        service.generate("conn-2", buildRequest());

        verify(ssePushService).sendError(eq("conn-2"), anyString(), contains("大纲生成失败"));
        verify(teachingContentService, never()).updateById(any());
        verify(ssePushService, never()).sendComplete(anyString(), anyMap());
    }

    private LessonPrepGenerateRequest buildRequest() {
        LessonPrepGenerateRequest req = new LessonPrepGenerateRequest();
        req.setUserId(7L);
        req.setTitle("化学方程式配平");
        req.setSubject("chemistry");
        req.setGrade("grade_9");
        req.setKnowledgePointIds(List.of(1L, 2L));
        req.setTeachingGoals(List.of("掌握配平方法"));
        req.setTotalHours(1);
        req.setStyle("standard");
        req.setWeakPointIds(List.of());
        return req;
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `mvn -o test -Dtest=LessonPrepGenerateServiceImplTest`
Expected: 编译失败 `cannot find symbol`（DTO/Service/Controller 未建）。Mockito 需要 pom 已有（spring-boot-starter-test 自带，确认 pom 中 `mockito` 依赖存在，若无则 Task 3 已加入——spring-boot-starter-test 已含 mockito-core）。

- [ ] **Step 3: 实现 DTO**

```java
package com.treepeople.leapmindtts.pojo.dto;

import lombok.Data;

import java.util.List;

/**
 * AI 备课生成请求（camelCase 与 Python /api/lesson-prep/generate 请求体一致）。
 */
@Data
public class LessonPrepGenerateRequest {
    /** 用户ID */
    private Long userId;
    /** 备课标题 */
    private String title;
    /** 科目: math/chinese/english/physics/chemistry/biology */
    private String subject;
    /** 年级: grade_7 ~ grade_12 */
    private String grade;
    /** 知识点ID列表 */
    private List<Long> knowledgePointIds;
    /** 教学目标列表 */
    private List<String> teachingGoals;
    /** 课时数 */
    private Integer totalHours = 1;
    /** 备课风格: standard/detailed/interactive */
    private String style = "standard";
    /** 薄弱知识点ID列表 */
    private List<Long> weakPointIds = List.of();
    /** 用户画像摘要（可选） */
    private String userProfileSummary;
}
```

- [ ] **Step 4: 实现服务接口与实现**

```java
package com.treepeople.leapmindtts.service;

import com.treepeople.leapmindtts.pojo.dto.LessonPrepGenerateRequest;

/**
 * AI 备课生成服务（Java → Python 管线）。
 */
public interface LessonPrepGenerateService {

    /**
     * 异步触发备课生成：透传 Python SSE 事件到 connectionId 对应连接，
     * done 后落库 teaching_contents。异常通过 error 事件告知前端。
     */
    void generate(String connectionId, LessonPrepGenerateRequest request);
}
```

```java
package com.treepeople.leapmindtts.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.treepeople.leapmindtts.integration.PythonAIClient;
import com.treepeople.leapmindtts.integration.SseEventParser.SseEvent;
import com.treepeople.leapmindtts.mapper.PyTeachingContentMapper;
import com.treepeople.leapmindtts.pojo.dto.LessonPrepGenerateRequest;
import com.treepeople.leapmindtts.pojo.entity.PyTeachingContent;
import com.treepeople.leapmindtts.pojo.entity.TeachingContent;
import com.treepeople.leapmindtts.service.LessonPrepGenerateService;
import com.treepeople.leapmindtts.service.TeachingContentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
@RequiredArgsConstructor
public class LessonPrepGenerateServiceImpl implements LessonPrepGenerateService {

    private static final String STATUS_DRAFT = "draft";
    private static final String STATUS_PUBLISHED = "published";

    private final PythonAIClient pythonAIClient;
    private final PyTeachingContentMapper pyTeachingContentMapper;
    private final SsePushServiceImpl ssePushService;
    private final TeachingContentService teachingContentService;
    private final ObjectMapper objectMapper;

    @Override
    public void generate(String connectionId, LessonPrepGenerateRequest request) {
        CompletableFuture.runAsync(() -> {
            try {
                TeachingContent content = createDraft(request);
                pythonAIClient.generate(toPythonRequestBody(request), event -> {
                    ssePushService.sendEvent(connectionId, event.type(), event.data());
                    handleEvent(connectionId, event, content);
                });
                Map<String, Object> result = new HashMap<>();
                result.put("prepId", content.getPrepId());
                result.put("status", content.getStatus());
                result.put("message", "备课生成完成");
                ssePushService.sendComplete(connectionId, result);
                log.info("备课生成完成，connectionId: {}, prepId: {}", connectionId, content.getPrepId());
            } catch (Exception e) {
                log.error("备课生成失败，connectionId: {}", connectionId, e);
                ssePushService.sendError(connectionId, "PREP_GENERATE_ERROR", e.getMessage());
            }
        });
    }

    private TeachingContent createDraft(LessonPrepGenerateRequest request) {
        TeachingContent content = new TeachingContent();
        content.setUserId(request.getUserId());
        content.setTitle(request.getTitle());
        content.setStatus(STATUS_DRAFT);
        teachingContentService.save(content);
        content.setPrepId(content.getId());
        teachingContentService.updateById(content);
        log.info("已创建草稿备课，prepId: {}", content.getPrepId());
        return content;
    }

    private void handleEvent(String connectionId, SseEvent event, TeachingContent content) {
        if ("done".equals(event.type())) {
            persistFromPython(event, content);
        } else if ("error".equals(event.type())) {
            throw new IllegalStateException(extractErrorMessage(event));
        }
    }

    private void persistFromPython(SseEvent event, TeachingContent content) {
        try {
            JsonNode node = objectMapper.readTree(event.data());
            int pyPrepId = node.path("prepId").asInt(-1);
            if (pyPrepId <= 0) {
                throw new IllegalStateException("done 事件缺少 prepId: " + event.data());
            }
            PyTeachingContent py = pyTeachingContentMapper.selectById(pyPrepId);
            if (py == null) {
                throw new IllegalStateException("py_teaching_contents 无记录 id=" + pyPrepId);
            }
            content.setTitle(py.getTitle());
            content.setStatus(STATUS_PUBLISHED);
            content.setPptStructure(py.getPptStructureJson());
            content.setGeneratedContentJson(py.getGeneratedContentJson());
            content.setSourcePrepId(py.getId().longValue());
            teachingContentService.updateById(content);
            log.info("备课落库完成，prepId: {}, sourcePrepId: {}", content.getPrepId(), py.getId());
        } catch (Exception e) {
            if (e instanceof IllegalStateException) {
                throw (IllegalStateException) e;
            }
            throw new IllegalStateException("Python 结果落库失败: " + e.getMessage(), e);
        }
    }

    private String extractErrorMessage(SseEvent event) {
        try {
            JsonNode node = objectMapper.readTree(event.data());
            return node.path("message").asText(event.data());
        } catch (Exception e) {
            return event.data();
        }
    }

    private Map<String, Object> toPythonRequestBody(LessonPrepGenerateRequest request) {
        Map<String, Object> body = new HashMap<>();
        body.put("userId", request.getUserId());
        body.put("title", request.getTitle());
        body.put("subject", request.getSubject());
        body.put("grade", request.getGrade());
        body.put("knowledgePointIds", request.getKnowledgePointIds());
        body.put("teachingGoals", request.getTeachingGoals());
        body.put("totalHours", request.getTotalHours());
        body.put("style", request.getStyle());
        body.put("weakPointIds", request.getWeakPointIds());
        if (request.getUserProfileSummary() != null) {
            body.put("userProfileSummary", request.getUserProfileSummary());
        }
        return body;
    }
}
```

- [ ] **Step 5: 实现 Controller**

```java
package com.treepeople.leapmindtts.controller.lesson;

import com.treepeople.leapmindtts.pojo.dto.LessonPrepGenerateRequest;
import com.treepeople.leapmindtts.pojo.result.ApiResponse;
import com.treepeople.leapmindtts.service.LessonPrepGenerateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * AI 备课生成控制器（Java → Python SSE 管线）。
 * 调用方式与 PptExportController 一致：前端先 GET /api/ppt/sse/{connectionId} 建连，再 POST 本端点。
 */
@Slf4j
@RestController
@RequestMapping("/api/lesson-prep")
@RequiredArgsConstructor
@Tag(name = "AI备课生成", description = "调用 Python 生成管线的备课接口")
public class LessonPrepGenerateController {

    private final LessonPrepGenerateService generateService;

    @PostMapping("/generate")
    @Operation(summary = "触发AI备课生成", description = "异步生成，事件经 /api/ppt/sse/{connectionId} 推送")
    public ResponseEntity<ApiResponse<Map<String, String>>> generate(
            @RequestBody LessonPrepGenerateRequest request) {
        log.info("触发AI备课生成，用户ID: {}, 标题: {}", request.getUserId(), request.getTitle());
        String connectionId = "prep-" + UUID.randomUUID().toString().substring(0, 8);
        generateService.generate(connectionId, request);
        Map<String, String> data = new HashMap<>();
        data.put("connectionId", connectionId);
        data.put("message", "备课生成已启动");
        return ResponseEntity.ok(ApiResponse.success(data, "任务已启动"));
    }
}
```

- [ ] **Step 6: 运行全部测试**

Run: `mvn -o test -Dtest=SseEventParserTest,PythonAIClientTest,LessonPrepGenerateServiceImplTest`
Expected: 全部 PASS。

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/treepeople/leapmindtts/pojo/dto/LessonPrepGenerateRequest.java src/main/java/com/treepeople/leapmindtts/service/LessonPrepGenerateService.java src/main/java/com/treepeople/leapmindtts/service/impl/LessonPrepGenerateServiceImpl.java src/main/java/com/treepeople/leapmindtts/controller/lesson/LessonPrepGenerateController.java src/test/java/com/treepeople/leapmindtts/service/impl/LessonPrepGenerateServiceImplTest.java
git commit -m "feat(m5): Java generate 端点，SSE 透传 Python 管线并落库"
```

---

### Task 6: 端到端联调与文档

**Files:**
- Modify: `leapmind/m5-lesson-prep-api.md`（在仓库根，补 Java 侧新接口）
- 只读：`java-server.log` / `python-server.log`（D:/leapmind1 根，联调时观察错误）

**Interfaces:**
- Consumes: 全部任务产物。本任务无代码产出（除非联调发现 bug——按 bug 修复走小提交）。

- [ ] **Step 1: 双服务启动**

终端 1（Python M5）：
```bash
cd D:/leapmind1/leapmind/aitutor-backend-python
PORT=8001 PYTHONIOENCODING=utf-8 .venv/Scripts/python.exe run.py
```

终端 2（Java，argfile 方式启动，路径反斜杠转正斜杠）：
```bash
cd D:/leapmind1/leapmind/aitutor-backend-java
java @args.txt
```
（args.txt 内容按记忆：`-cp D:/leapmind1/leapmind/aitutor-backend-java/target/classes;...` 等依赖 classpath 的完整 argfile。若 target 未构建，先 `mvn -o package -DskipTests`。启动后确认 8080 端口监听。）

- [ ] **Step 2: 全链路 curl 验证**

```bash
# 1) 建立 SSE 连接（后台挂起）
curl -s -N http://localhost:8080/api/ppt/sse/prep-smoke1 > /tmp/sse-out.txt &
# 2) 触发生成（真实请求，~16s）
curl -s -X POST http://localhost:8080/api/lesson-prep/generate \
  -H "Content-Type: application/json" \
  -d '{"userId":1,"title":"打通测试","subject":"chemistry","grade":"grade_9","knowledgePointIds":[],"teachingGoals":["验证链路"],"totalHours":1,"style":"standard","weakPointIds":[]}'
# 3) 等 20s 后查看 SSE 流
sleep 20 && cat /tmp/sse-out.txt
```

Expected: 步骤 2 返回 `{"code":200,"data":{"connectionId":"prep-smoke1","message":"备课生成已启动"}}`；步骤 3 的事件流依次为 `connected`（建连时）、`outline`、`section`×N、`complete`（含 `prepId`）。若收到 `error` 事件，检查 Java 日志与 Python 日志定位（多为 session 过期/端口错误）。

- [ ] **Step 3: 验证双表落库**

```bash
mysql -uroot -p1234 leapmind-voice -e "
SELECT id, prep_id, title, status, source_prep_id, LEFT(ppt_structure, 40) AS ppt_head
FROM teaching_contents ORDER BY id DESC LIMIT 3;
SELECT id, title, status, LEFT(ppt_structure_json, 40) AS ppt_head
FROM py_teaching_contents ORDER BY id DESC LIMIT 3;"
```

Expected: Java 表最新行 `status=published`、`source_prep_id` 非空、`ppt_head` 为 `[{"page_num":1`；Python 表最新行 `status=published`。两行 `title` 一致，`source_prep_id` 等于 Python 表 `id`。

- [ ] **Step 4: 重复调用幂等验证**

再次执行 Step 2/3（换 connectionId）。Expected: 第二次生成成功，Java 表出现新的一行 published 记录，无重复/失败。确认 Python 端只收到一次 session 登录（Java 日志出现一次 "Python 服务登录成功"）。

- [ ] **Step 5: 验证前端兼容路径（可选，前端代码不在本仓库）**

前端创建页目前走"先建记录再 PUT 同步"的旧流程，本计划不强制改前端。在 `m5-lesson-prep-api.md` 中记录新旧流程并存说明：新端点供前端后续切换；切换前 Java 表记录由旧流程维护，切换后由本接口维护。

- [ ] **Step 6: 更新 m5-lesson-prep-api.md**

在文档中新增章节（内容如下，位置：API 列表末尾）：

```markdown
## Java 侧新端点（2026-08-04 打通后新增）

### POST /api/lesson-prep/generate（Java 8080）
触发 AI 备课生成。SSE 事件经现有 GET /api/ppt/sse/{connectionId} 推送。

请求体（camelCase，与 Python /api/lesson-prep/generate 一致）：
{
  "userId": 1, "title": "...", "subject": "chemistry", "grade": "grade_9",
  "knowledgePointIds": [], "teachingGoals": ["..."], "totalHours": 1,
  "style": "standard", "weakPointIds": []
}

调用流程：
1. 前端 GET /api/ppt/sse/prep-xxxx 建立 SSE 连接（透传 `connected` 事件）
2. POST /api/lesson-prep/generate，返回 {"connectionId":"prep-xxxx","message":"备课生成已启动"}
3. SSE 事件依次到达：outline → section×N → slide×N → slides_done → narration×N → complete
4. complete 携带 {"prepId":N,"status":"published","message":"备课生成完成"}

数据落库：
- Python 侧：py_teaching_contents（生成结果完整归档，UTC 时间戳）
- Java 侧：teaching_contents 新行（draft→published，prep_id=自身id，source_prep_id=Python表id，
  ppt_structure=py.ppt_structure_json，generated_content_json=py.generated_content_json）
- 失败：SSE error 事件，Java 表保留 draft 记录（前端可删除）

已知约束：
- Python session 由 Java 自动登录缓存（admin/admin123，application.yml 可配），401 自动重登重试
- Python 服务必须跑在 8001（M3 占 8000）
```

- [ ] **Step 7: Commit**

```bash
git add m5-lesson-prep-api.md
git commit -m "docs(m5): 记录 Java generate 端点与双表落库契约"
```

---

## Self-Review 记录

- **Spec 覆盖**：方式一的全部承诺均有任务承接——端口固化（T1）、SSE 透传（T2 解析器 + T3 客户端 + T5 服务）、数据分别落表（T4 实体/迁移 + T5 落库规则）、session 认证与 401 重试（T3）、时间戳处理（全局约束：Java 不读 Python 时间戳，Python 代码零改动）、文档（T6）。已知坑（cookie 不设置、camelCase、8000/8001 冲突）全部落入对应任务的约束与步骤。
- **类型一致性**：`SseEvent(type,data)` 在 T2 定义、T3/T5 使用，字段名统一；`LessonPrepGenerateRequest` 字段与 Python `LessonPrepRequest` 别名一致；`PyTeachingContent` 列名与 Python models.py 完全对应（generated_content_json、ppt_structure_json）；`source_prep_id` 在 T4 迁移、T5 落库、T6 验证中一致。
- **占位符扫描**：所有代码步骤均含完整代码，无 TBD/TODO；唯一未写死的是 Task 6 的 argfile 启动命令（依赖构建产物路径，已给出说明性命令和验证方法，属于联调环境事实而非占位）。

## 风险与依赖

- **Python 生成耗时**（~16s，PPT 单独 83s）：T3 超时默认 300s，T6 联调步骤的 sleep 20 只覆盖 generate 三阶段；若验证 generate-ppt 需要更长等待，不阻塞本计划（generate-ppt 打通不在范围内）。
- **Python 服务 key 依赖**：generate 需要 LLM key（DeepSeek 等）与网络，Task 1 Step 4 若 key 缺失会失败——此时跳过 Step 4，以 Task 3 的 MockWebServer 测试作为事件格式的唯一事实来源，并记录到计划注释。
- **Java 进程启动**：无热重载，改代码须重启；联调时注意 `java @args.txt` 的 classpath 完整（含新类的编译产物，先 `mvn -o package -DskipTests`）。
- **测试前置**：Task 3 的 MockWebServer 4.11.0 与 spring-boot-starter-test 已在 pom.xml（test 作用域），无需新增依赖。
