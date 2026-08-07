package com.treepeople.leapmindtts.service.lesson;

import reactor.core.publisher.Flux;

import java.util.Map;

/**
 * AI SSE 流式讲课生成接口（许沣睿）
 */
public interface AISseService {

    String generateTeachingContent(String courseId, String sourceText, Map<String, Object> userProfile);

    Flux<String> streamGenerateTeachingContent(String courseId, String sourceText, Map<String, Object> userProfile);
}
