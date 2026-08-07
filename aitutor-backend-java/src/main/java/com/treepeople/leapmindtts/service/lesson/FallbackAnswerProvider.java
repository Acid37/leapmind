package com.treepeople.leapmindtts.service.lesson;

import org.springframework.stereotype.Service;

@Service
public class FallbackAnswerProvider {
    public String getFallbackAnswer(FallbackScene scene) {
        return "Fallback for " + scene.name();
    }
}
