package com.treepeople.leapmindtts.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.io.ClassPathResource;

class M6ProfileEngineConfigurationTest {
    @Test
    void applicationConfigurationEnablesHttpEngineAndProjection() throws IOException {
        var sources = new YamlPropertySourceLoader().load(
                "application.yml",
                new ClassPathResource("application.yml"));
        var properties = sources.get(0);

        assertEquals(Boolean.TRUE, properties.getProperty("m6.profile-engine.enabled"));
        assertEquals(Boolean.TRUE, properties.getProperty("m6.profile-engine.projection-enabled"));
        assertEquals("http://localhost:8000", properties.getProperty("m6.profile-engine.base-url"));
        assertEquals("/api/internal/ai/build-profile",
                properties.getProperty("m6.profile-engine.build-profile-path"));
    }
}
