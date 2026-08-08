package com.treepeople.leapmindtts.pojo.dto;

import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
public class ConversationRequest {
    private Long userId;
    private String sessionId;
    private String question;
    private SceneType sceneType;
    private Map<String, Object> context;
    private InputType inputType;
    private List<String> attachmentUrls;

    public enum SceneType {
        doing_exercise, explaining, teaching, lesson_prep, general_qa
    }

    public enum InputType {
        text, voice, image
    }
}
