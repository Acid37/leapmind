package com.treepeople.leapmindtts.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.cors.CorsConfiguration;

class CorsConfigTest {

    @Test
    void configuredOriginsAndM6DiagnosticHeadersAreAvailableToBrowsers() {
        CorsConfig config = new CorsConfig("http://localhost:5173, http://192.168.10.55:5173");
        MockHttpServletRequest request = new MockHttpServletRequest("OPTIONS", "/api/user-profile/22");
        CorsConfiguration cors = config.corsConfigurationSource().getCorsConfiguration(request);

        assertEquals(List.of("http://localhost:5173", "http://192.168.10.55:5173"), cors.getAllowedOrigins());
        assertEquals("http://192.168.10.55:5173", cors.checkOrigin("http://192.168.10.55:5173"));
        assertNull(cors.checkOrigin("http://malicious.example"));
        assertTrue(cors.getAllowCredentials());
        assertTrue(cors.getExposedHeaders().containsAll(List.of("X-Request-Id", "Deprecation", "Link")));
    }

    @Test
    void wildcardOriginsAreRejectedWhenCredentialsAreAllowed() {
        assertThrows(IllegalArgumentException.class,
                () -> new CorsConfig("http://localhost:5173,https://*.example.com"));
    }
}
