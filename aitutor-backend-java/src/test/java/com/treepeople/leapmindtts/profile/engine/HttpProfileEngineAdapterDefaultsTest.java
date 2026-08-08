package com.treepeople.leapmindtts.profile.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import com.treepeople.leapmindtts.config.PythonInternalAiProperties;
import com.treepeople.leapmindtts.service.profile.engine.DisabledProfileEngineAdapter;
import com.treepeople.leapmindtts.service.profile.engine.HttpProfileEngineAdapter;
import com.treepeople.leapmindtts.service.profile.engine.ProfileEnginePort;
import com.treepeople.leapmindtts.service.profile.platform.ProfilePlatformDefaultsConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.web.reactive.function.client.WebClient;

class HttpProfileEngineAdapterDefaultsTest {
    @Test void disabledByDefaultAndDisabledAdapterBacksOff() {
        new ApplicationContextRunner()
                .withUserConfiguration(EngineConfig.class)
                .run(context -> {
                    assertEquals(1, context.getBeansOfType(ProfileEnginePort.class).size());
                    assertInstanceOf(DisabledProfileEngineAdapter.class, context.getBean(ProfileEnginePort.class));
                    assertEquals(0, context.getBeansOfType(HttpProfileEngineAdapter.class).size());
                });
    }

    @Test void registeredWhenEnabledAndDisabledAdapterBacksOff() {
        new ApplicationContextRunner()
                .withPropertyValues("m6.profile-engine.enabled=true",
                        "m6.profile-engine.base-url=http://localhost:18001")
                .withUserConfiguration(EngineConfig.class)
                .run(context -> {
                    assertEquals(1, context.getBeansOfType(ProfileEnginePort.class).size());
                    assertInstanceOf(HttpProfileEngineAdapter.class, context.getBean(ProfileEnginePort.class));
                    assertEquals(0, context.getBeansOfType(DisabledProfileEngineAdapter.class).size());
                });
    }

    @Configuration
    @EnableConfigurationProperties(PythonInternalAiProperties.class)
    @Import({HttpProfileEngineAdapter.class, ProfilePlatformDefaultsConfiguration.class})
    static class EngineConfig {
        @Bean
        WebClient.Builder webClientBuilder() {
            return WebClient.builder();
        }
    }
}
