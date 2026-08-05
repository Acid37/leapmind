package com.treepeople.leapmindtts.pojo.dto;

import com.treepeople.leapmindtts.pojo.dto.ConversationRequest.SceneType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ConversationSession {
    private String sessionId;
    private Long userId;
    private SceneType sceneType;
    private Map<String, Object> context;
    private List<Map<String, String>> messages = new ArrayList<>();
    private long createdAt;
    private long updatedAt;
}
