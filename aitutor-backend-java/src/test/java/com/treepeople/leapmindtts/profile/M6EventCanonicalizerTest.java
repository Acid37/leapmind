package com.treepeople.leapmindtts.profile;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.treepeople.leapmindtts.service.profile.validation.EventPayloadCanonicalizer;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class M6EventCanonicalizerTest {
    private final ObjectMapper json=new ObjectMapper();
    @Test void sortsKeysNormalizesNumbersUnicodeAndMissingNull() throws Exception {
        String one=new String(EventPayloadCanonicalizer.canonical(json.readTree("{\"b\":1.0,\"a\":\"é\",\"optional\":null}")),StandardCharsets.UTF_8);
        String two=new String(EventPayloadCanonicalizer.canonical(json.readTree("{\"a\":\"é\",\"b\":1}")),StandardCharsets.UTF_8);
        assertEquals(two,one);assertEquals("{\"a\":\"é\",\"b\":1}",one);
    }
}
