
 package com.treepeople.leapmindtts;
 
 import io.github.resilience4j.circuitbreaker.CircuitBreaker;
 import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
 import io.github.resilience4j.ratelimiter.RateLimiter;
 import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
 import org.junit.jupiter.api.AfterEach;
 import org.springframework.beans.factory.annotation.Autowired;
 import org.springframework.boot.test.context.SpringBootTest;
 import org.springframework.test.context.ActiveProfiles;
 
 /**
  * Base class for integration tests to provide common setup and teardown logic.
  * This includes resetting the state of Resilience4j components after each test
  * to ensure test isolation and prevent state pollution.
  */
 @SpringBootTest
 @ActiveProfiles("test")
 public abstract class BaseIntegrationTest {
 
     @Autowired(required = false)
     private CircuitBreakerRegistry circuitBreakerRegistry;
 
     @Autowired(required = false)
     private RateLimiterRegistry rateLimiterRegistry;
 
     @AfterEach
    public void resetResilience4jState() {
        // 仅保留熔断器的状态重置
        if (circuitBreakerRegistry != null) {
            circuitBreakerRegistry.getAllCircuitBreakers()
                    .forEach(CircuitBreaker::reset);
        }
    }
 }
 