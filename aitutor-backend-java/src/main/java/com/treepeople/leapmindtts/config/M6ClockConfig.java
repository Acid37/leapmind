package com.treepeople.leapmindtts.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class M6ClockConfig {
    @Bean
    public Clock m6Clock() {
        return Clock.systemUTC();
    }
}
