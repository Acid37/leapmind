package com.treepeople.leapmindtts.service.practice;

import com.treepeople.leapmindtts.pojo.entity.PracticeQuestion;
import com.treepeople.leapmindtts.util.PracticeKnowledgePointIds;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.http.HttpMethod.POST;

class WrongQuestionEventPublisherTest {

    private MockRestServiceServer server;
    private WrongQuestionEventPublisher publisher;
    private PracticeQuestion question;

    @BeforeEach
    void setUp() {
        RestTemplate restTemplate = new RestTemplate();
        server = MockRestServiceServer.bindTo(restTemplate).build();
        publisher = new WrongQuestionEventPublisher(restTemplate, "http://localhost:8080/");
        question = new PracticeQuestion();
        question.setId(5001L);
        question.setSubject("数学");
        question.setKnowledgePoint("二次函数");
    }

    @Test
    void postsTheDocumentedWrongQuestionEvent() {
        long kpId = PracticeKnowledgePointIds.from("数学", "二次函数");
        server.expect(once(), requestTo("http://localhost:8080/api/user-profile/1001/record-event"))
                .andExpect(method(POST))
                .andExpect(header(HttpHeaders.CONTENT_TYPE, startsWith(MediaType.APPLICATION_JSON_VALUE)))
                .andExpect(jsonPath("$.eventId", startsWith("m1-wrong:")))
                .andExpect(jsonPath("$.eventType").value("wrong_question_changed"))
                .andExpect(jsonPath("$.sourceModule").value("M1"))
                .andExpect(jsonPath("$.schemaVersion").value("1.0"))
                .andExpect(jsonPath("$.userId").value(1001))
                .andExpect(jsonPath("$.kpId").value(kpId))
                .andExpect(jsonPath("$.sessionId").value("session-abc"))
                .andExpect(jsonPath("$.data.questionId").value(5001))
                .andExpect(jsonPath("$.data.status").value("UNRESOLVED"))
                .andExpect(jsonPath("$.data.wrongCount").value(1))
                .andRespond(withSuccess());

        publisher.publishBestEffort(1001L, question, "UNRESOLVED", 1, "session-abc");

        server.verify();
    }

    @Test
    void httpFailureDoesNotEscapeToTheMistakeFlow() {
        server.expect(once(), requestTo("http://localhost:8080/api/user-profile/1001/record-event"))
                .andRespond(withServerError());

        assertDoesNotThrow(() -> publisher.publishBestEffort(
                1001L, question, "RESOLVED", 2, null));

        server.verify();
    }
}
