
 package com.treepeople.leapmindtts.listeners;
 
 import com.treepeople.leapmindtts.service.common.MetricsService;
 import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
 import jakarta.annotation.PostConstruct;
 import lombok.RequiredArgsConstructor;
 import lombok.extern.slf4j.Slf4j;
 import org.springframework.stereotype.Component;
 
 @Slf4j
 @Component
 @RequiredArgsConstructor
 public class CircuitBreakerEventListener {
 
     private final CircuitBreakerRegistry registry;
     private final MetricsService metrics;
 
     @PostConstruct
     public void registerListener() {
         registry.getAllCircuitBreakers().forEach(cb -> {
             log.info("Registering state transition listener for circuit breaker: {}", cb.getName());
             cb.getEventPublisher().onStateTransition(event -> {
                 log.warn("Circuit breaker '{}' state changed from {} to {}",
                         event.getCircuitBreakerName(),
                         event.getStateTransition().getFromState(),
                         event.getStateTransition().getToState());
                 metrics.recordCircuitBreakerState(
                     event.getCircuitBreakerName(),
                     event.getStateTransition().getFromState().name(),
                     event.getStateTransition().getToState().name()
                 );
             });
         });
     }
 }
 