package com.treepeople.leapmindtts.service.profile.platform;

import com.treepeople.leapmindtts.service.profile.engine.DisabledProfileEngineAdapter;
import com.treepeople.leapmindtts.service.profile.engine.ProfileEnginePort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Replacable disabled defaults; no ambient component-scanning race with future integrations. */
@Configuration
public class ProfilePlatformDefaultsConfiguration {
    @Bean @ConditionalOnMissingBean(PlatformCapabilityPolicy.class)
    PlatformCapabilityPolicy platformCapabilityPolicy() { return new DefaultDenyPlatformCapabilityPolicy(); }
    @Bean @ConditionalOnMissingBean(LearningEventPublisher.class)
    LearningEventPublisher learningEventPublisher(PlatformCapabilityPolicy policy) { return new DisabledLearningEventPublisher(policy); }
    @Bean @ConditionalOnMissingBean(ProfileContextProvider.class)
    ProfileContextProvider profileContextProvider(PlatformCapabilityPolicy policy) { return new DisabledProfileContextProvider(policy); }
    @Bean @ConditionalOnMissingBean(ProfileEnginePort.class)
    ProfileEnginePort profileEnginePort() { return new DisabledProfileEngineAdapter(); }
    @Bean @ConditionalOnMissingBean(PlatformIntegrationReadiness.class)
    PlatformIntegrationReadiness platformIntegrationReadiness() { return new PlatformIntegrationReadiness(); }
}
