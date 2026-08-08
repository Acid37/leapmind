package com.treepeople.leapmindtts.profile.platform;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

import com.treepeople.leapmindtts.service.profile.engine.ProfileEnginePort;
import com.treepeople.leapmindtts.service.profile.platform.LearningEventPublisher;
import com.treepeople.leapmindtts.service.profile.platform.PlatformCapabilityPolicy;
import com.treepeople.leapmindtts.service.profile.platform.ProfileContextProvider;
import com.treepeople.leapmindtts.service.profile.platform.PlatformIntegrationReadiness;
import com.treepeople.leapmindtts.service.profile.platform.ProfilePlatformDefaultsConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class PlatformDefaultsConfigurationTest {
    @Test void suppliesOneDisabledDefaultForEachPort() {
        new ApplicationContextRunner()
                .withUserConfiguration(ProfilePlatformDefaultsConfiguration.class)
                .run(context -> {
                    assertEquals(1, context.getBeansOfType(PlatformCapabilityPolicy.class).size());
                    assertEquals(1, context.getBeansOfType(LearningEventPublisher.class).size());
                    assertEquals(1, context.getBeansOfType(ProfileContextProvider.class).size());
                    assertEquals(1, context.getBeansOfType(ProfileEnginePort.class).size());
                    assertEquals(1, context.getBeansOfType(PlatformIntegrationReadiness.class).size());
                });
    }

    @Test void explicitPortsReplaceAllDefaults() {
        PlatformCapabilityPolicy policy = mock(PlatformCapabilityPolicy.class);
        LearningEventPublisher publisher = mock(LearningEventPublisher.class);
        ProfileContextProvider provider = mock(ProfileContextProvider.class);
        ProfileEnginePort engine = mock(ProfileEnginePort.class);
        new ApplicationContextRunner()
                .withBean(PlatformCapabilityPolicy.class, () -> policy)
                .withBean(LearningEventPublisher.class, () -> publisher)
                .withBean(ProfileContextProvider.class, () -> provider)
                .withBean(ProfileEnginePort.class, () -> engine)
                .withUserConfiguration(ProfilePlatformDefaultsConfiguration.class)
                .run(context -> {
                    assertEquals(1, context.getBeansOfType(PlatformCapabilityPolicy.class).size());
                    assertSame(policy, context.getBean(PlatformCapabilityPolicy.class));
                    assertSame(publisher, context.getBean(LearningEventPublisher.class));
                    assertSame(provider, context.getBean(ProfileContextProvider.class));
                    assertSame(engine, context.getBean(ProfileEnginePort.class));
                });
    }
}
