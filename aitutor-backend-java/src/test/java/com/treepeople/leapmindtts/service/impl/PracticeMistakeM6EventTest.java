package com.treepeople.leapmindtts.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.treepeople.leapmindtts.controller.PracticeController;
import com.treepeople.leapmindtts.mapper.PracticeMistakeMapper;
import com.treepeople.leapmindtts.mapper.PracticeQuestionMapper;
import com.treepeople.leapmindtts.pojo.entity.PracticeMistake;
import com.treepeople.leapmindtts.pojo.entity.PracticeQuestion;
import com.treepeople.leapmindtts.service.EventCollectionService;
import com.treepeople.leapmindtts.service.practice.WrongQuestionEventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PracticeMistakeM6EventTest {

    private final PracticeQuestionMapper questionMapper = mock(PracticeQuestionMapper.class);
    private final PracticeMistakeMapper mistakeMapper = mock(PracticeMistakeMapper.class);
    private final EventCollectionService eventCollectionService = mock(EventCollectionService.class);
    private final WrongQuestionEventPublisher eventPublisher = mock(WrongQuestionEventPublisher.class);
    private PracticeServiceImpl service;
    private PracticeQuestion question;

    @BeforeEach
    void setUp() {
        reset(questionMapper, mistakeMapper, eventCollectionService, eventPublisher);
        service = new PracticeServiceImpl(
                questionMapper,
                null,
                null,
                mistakeMapper,
                null,
                null,
                null,
                eventCollectionService,
                eventPublisher,
                new ObjectMapper(),
                null,
                null);
        question = new PracticeQuestion();
        question.setId(501L);
        question.setSubject("数学");
        question.setKnowledgePoint("二次函数");
    }

    @Test
    void publishesFirstWrongAnswer() {
        when(mistakeMapper.selectOne(any())).thenReturn(null);
        when(mistakeMapper.insert(any())).thenReturn(1);

        syncMistake(false, "session-1");

        verify(eventPublisher).publishBestEffort(
                7L, question, "UNRESOLVED", 1, "session-1");
    }

    @Test
    void publishesWhenResolvedMistakeBecomesWrongAgain() {
        when(mistakeMapper.selectOne(any())).thenReturn(mistake("RESOLVED", 2));
        when(mistakeMapper.update(any(), any())).thenReturn(1);

        syncMistake(false, "session-2");

        verify(eventPublisher).publishBestEffort(
                7L, question, "UNRESOLVED", 3, "session-2");
    }

    @Test
    void skipsEventWhenAnotherWrongAnswerDoesNotChangeStatus() {
        when(mistakeMapper.selectOne(any())).thenReturn(mistake("UNRESOLVED", 2));
        when(mistakeMapper.update(any(), any())).thenReturn(1);

        syncMistake(false, "session-3");

        verify(eventPublisher, never()).publishBestEffort(any(), any(), any(), anyInt(), any());
    }

    @Test
    void publishesResolvedWhenCorrectAnswerChangesStatus() {
        when(mistakeMapper.selectOne(any())).thenReturn(mistake("REVIEWING", 4));
        when(mistakeMapper.update(any(), any())).thenReturn(1);

        syncMistake(true, "session-4");

        verify(eventPublisher).publishBestEffort(
                7L, question, "RESOLVED", 4, "session-4");
    }

    @Test
    void publishesOnlyWhenManualStatusActuallyChanges() {
        PracticeMistake mistake = mistake("UNRESOLVED", 4);
        when(mistakeMapper.selectOne(any())).thenReturn(mistake);
        when(mistakeMapper.update(any(), any())).thenReturn(1);
        when(questionMapper.selectById(501L)).thenReturn(question);
        PracticeController.UpdateMistakeRequest request = new PracticeController.UpdateMistakeRequest();
        request.setStatus("reviewing");

        service.updateMistakeStatus(7L, 91L, request);

        verify(eventPublisher).publishBestEffort(
                7L, question, "REVIEWING", 4, null);

        reset(eventPublisher);
        mistake.setStatus("REVIEWING");
        service.updateMistakeStatus(7L, 91L, request);
        verify(eventPublisher, never()).publishBestEffort(any(), any(), any(), anyInt(), any());
    }

    private void syncMistake(boolean correct, String sessionId) {
        ReflectionTestUtils.invokeMethod(service, "syncMistakeBook", 7L, question, correct, sessionId);
    }

    private PracticeMistake mistake(String status, int wrongCount) {
        PracticeMistake mistake = new PracticeMistake();
        mistake.setId(91L);
        mistake.setUserId(7L);
        mistake.setQuestionId(501L);
        mistake.setStatus(status);
        mistake.setWrongCount(wrongCount);
        mistake.setReviewCount(0);
        return mistake;
    }
}
