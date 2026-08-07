package com.treepeople.leapmindtts.profile;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.treepeople.leapmindtts.exception.M6ApiException;
import com.treepeople.leapmindtts.mapper.UserProfileMapper;
import com.treepeople.leapmindtts.pojo.dto.profile.M6ProfileDtos.*;
import com.treepeople.leapmindtts.pojo.entity.UserKnowledgeMastery;
import com.treepeople.leapmindtts.pojo.entity.UserProfile;
import com.treepeople.leapmindtts.service.profile.cache.M6ProfileCache;
import com.treepeople.leapmindtts.service.profile.impl.ProfileSnapshotReader;
import com.treepeople.leapmindtts.service.profile.impl.UserProfileQueryServiceImpl;
import com.treepeople.leapmindtts.service.profile.security.ProfileActorResolver;
import com.treepeople.leapmindtts.service.profile.summary.SceneSummaryAssembler;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.mock.web.MockHttpServletRequest;

class M6ProfileQueryAndCacheTest {
    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();

    @Test void knowledgeRejectsDuplicatesAndKeepsUniqueRequestOrder() {
        UserProfileMapper profiles = mock(UserProfileMapper.class);
        ProfileSnapshotReader snapshots = mock(ProfileSnapshotReader.class);
        ProfileActorResolver actor = mock(ProfileActorResolver.class);
        UserProfileQueryServiceImpl service = service(actor, profiles, snapshots, disabledCache());
        assertEquals("NOT_READY", ((NotReadyProfile) service.profile(1001L, new MockHttpServletRequest())).profileStatus());
        KnowledgeStatusResponse rows = (KnowledgeStatusResponse) service.knowledge(1001L, List.of(9L, 7L), new MockHttpServletRequest());
        assertEquals(List.of(9L, 7L), rows.knowledge().stream().map(KnowledgeContext::kpId).toList());
        M6ApiException duplicate = assertThrows(M6ApiException.class,
                () -> service.knowledge(1001L, List.of(9L, 7L, 9L), new MockHttpServletRequest()));
        assertEquals("PROFILE_EVENT_INVALID", duplicate.getErrorCode());
        M6ApiException prep = assertThrows(M6ApiException.class, () -> service.summary(777L, "lesson_prep", null, new MockHttpServletRequest()));
        assertEquals("PROFILE_ACCESS_DENIED", prep.getErrorCode());
        verify(actor).requireActor(any());
        verify(actor, never()).authorizeSelf(any(), eq(777L));
        verify(profiles, never()).selectVersionStamp(777L);
    }

    @Test void staleProfileUsesOneSnapshotAndDoesNotExposeRawProfileJson() throws Exception {
        UserProfileMapper profiles = mock(UserProfileMapper.class);
        ProfileSnapshotReader snapshots = mock(ProfileSnapshotReader.class);
        ProfileActorResolver actor = mock(ProfileActorResolver.class);
        UserProfile row = visible("STALE", "ENGINE_TIMEOUT");
        when(profiles.selectVersionStamp(1001L)).thenReturn(stamp(row));
        when(snapshots.read(1001L)).thenReturn(new ProfileSnapshot(row, List.of(mastery(3L))));
        FullProfile result = (FullProfile) service(actor, profiles, snapshots, disabledCache()).profile(1001L, new MockHttpServletRequest());
        assertEquals("STALE", result.profileStatus());
        assertEquals("ENGINE_TIMEOUT", result.statusReason());
        assertEquals(List.of(3L), result.knowledge().stream().map(KnowledgeContext::kpId).toList());
        verify(snapshots, times(1)).read(1001L);
    }

    @Test void explainingAndConversationHaveDifferentTypedFieldsAndAliasSharesKey() throws Exception {
        UserProfileMapper profiles = mock(UserProfileMapper.class);
        ProfileSnapshotReader snapshots = mock(ProfileSnapshotReader.class);
        UserProfile row = visible("READY", null);
        when(profiles.selectVersionStamp(1001L)).thenReturn(stamp(row));
        when(snapshots.read(1001L)).thenReturn(new ProfileSnapshot(row, List.of(mastery(3L))));
        UserProfileQueryServiceImpl service = service(mock(ProfileActorResolver.class), profiles, snapshots, disabledCache());
        Object explaining = service.summary(1001L, "photo_qa", 3L, new MockHttpServletRequest());
        Object conversation = service.summary(1001L, "conversation", 3L, new MockHttpServletRequest());
        assertInstanceOf(ExplainingSummary.class, explaining);
        assertInstanceOf(ConversationSummary.class, conversation);
        assertEquals("explaining", ((ExplainingSummary) explaining).sceneType());
        String encoded=json.writeValueAsString(explaining);assertTrue(encoded.codePointCount(0,encoded.length())<=1200);
        assertEquals(M6ProfileCache.summaryKey(1001L, "explaining", 3L), M6ProfileCache.summaryKey(1001L, "photo_qa", 3L));
    }

    @Test void redisStampIncludesStatusReasonAndUsesCanonicalTtl() throws Exception {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked") ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        M6ProfileCache cache = new M6ProfileCache(redis, json, true);
        cache.put("p", 4, "STALE", "TIMEOUT", "stamp", json.readTree("{\"ok\":true}"), false);
        verify(values).set(eq("p"), anyString(), eq(java.time.Duration.ofMinutes(30)));
        when(values.get("p")).thenReturn("{\"cacheSchema\":\"1.0\",\"profileVersion\":4,\"profileStatus\":\"STALE\",\"statusReason\":\"OTHER\",\"computedAt\":\"stamp\",\"data\":{}}");
        assertNull(cache.get("p", 4, "STALE", "TIMEOUT", "stamp"));
        verify(redis).delete("p");
        assertEquals("user:profile:1", M6ProfileCache.profileKey(1));
    }

    @Test void disabledCachePerformsNoRedisOperation() {
        StringRedisTemplate redis=mock(StringRedisTemplate.class);M6ProfileCache cache=new M6ProfileCache(redis,json,false);
        assertNull(cache.get("x",1,"READY",null,"stamp"));cache.put("x",1,"READY",null,"stamp",json.createObjectNode(),false);cache.delete("x");verifyNoInteractions(redis);
    }

    @Test void redisFailuresProduceOnlySanitizedStructuredWarnings() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked") ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.get(anyString())).thenThrow(new org.springframework.dao.DataAccessResourceFailureException("secret-value-must-not-log"));
        ch.qos.logback.classic.Logger logger = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(M6ProfileCache.class);
        ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent> events = new ch.qos.logback.core.read.ListAppender<>();
        events.start(); logger.addAppender(events);
        try {
            assertNull(new M6ProfileCache(redis, json, true).get("user:profile:991", 1, "READY", null, "stamp"));
            String message = events.list.get(0).getFormattedMessage();
            assertTrue(message.contains("operation=GET") && message.contains("cacheType=PROFILE")
                    && message.contains("exceptionType=DataAccessResourceFailureException"));
            assertFalse(message.contains("user:profile:991") || message.contains("secret-value-must-not-log")
                    || message.contains("requestId=null"));
        } finally {
            logger.detachAppender(events);
        }
    }

    @Test void invalidProfileJsonAndIncompleteStaleAreSanitized() {
        UserProfileMapper profiles=mock(UserProfileMapper.class);ProfileSnapshotReader snapshots=mock(ProfileSnapshotReader.class);UserProfile broken=visible("READY",null);broken.setProfileDataJson("[]");when(profiles.selectVersionStamp(1001L)).thenReturn(stamp(broken));when(snapshots.read(1001L)).thenReturn(new ProfileSnapshot(broken,List.of()));
        assertEquals("PROFILE_SERVICE_DEGRADED",assertThrows(M6ApiException.class,()->service(mock(ProfileActorResolver.class),profiles,snapshots,disabledCache()).profile(1001L,new MockHttpServletRequest())).getErrorCode());
        UserProfile incomplete=stamp(visible("STALE","TIMEOUT"));incomplete.setComputedAt(null);when(profiles.selectVersionStamp(1001L)).thenReturn(incomplete);
        assertEquals("PROFILE_SERVICE_DEGRADED",assertThrows(M6ApiException.class,()->service(mock(ProfileActorResolver.class),profiles,snapshots,disabledCache()).profile(1001L,new MockHttpServletRequest())).getErrorCode());
    }

    @Test void typedProfileAcceptsFullNestedContractAndRejectsIllegalNestedObjects() {
        UserProfileMapper profiles = mock(UserProfileMapper.class);
        ProfileSnapshotReader snapshots = mock(ProfileSnapshotReader.class);
        UserProfile full = visible("READY", null);
        full.setRecentFocusJson("[{\"kpId\":3,\"weight\":0.75}]");
        full.setProfileDataJson("{\"recentConfusions\":[{\"kpId\":3,\"detail\":\"step unclear\",\"evidenceCount\":2,\"confidence\":0.8,\"lastOccurredAt\":\"2026-07-20T10:00:00Z\"}]}");
        when(profiles.selectVersionStamp(1001L)).thenReturn(stamp(full));
        when(snapshots.read(1001L)).thenReturn(new ProfileSnapshot(full, List.of(mastery(3L))));
        FullProfile accepted = (FullProfile) service(mock(ProfileActorResolver.class), profiles, snapshots, disabledCache())
                .profile(1001L, new MockHttpServletRequest());
        assertEquals(new RecentFocus(3L, new BigDecimal("0.75")), accepted.recentFocus().get(0));
        assertEquals("step unclear", accepted.recentConfusions().get(0).detail());

        UserProfile invalid = visible("READY", null);
        invalid.setProfileDataJson("{\"recentConfusions\":[{\"kpId\":3,\"detail\":\"x\",\"evidenceCount\":1,\"confidence\":0.8,\"lastOccurredAt\":\"2026-07-20T10:00:00Z\",\"extra\":true}]}");
        when(profiles.selectVersionStamp(1001L)).thenReturn(stamp(invalid));
        when(snapshots.read(1001L)).thenReturn(new ProfileSnapshot(invalid, List.of()));
        assertEquals("PROFILE_SERVICE_DEGRADED", assertThrows(M6ApiException.class,
                () -> service(mock(ProfileActorResolver.class), profiles, snapshots, disabledCache())
                        .profile(1001L, new MockHttpServletRequest())).getErrorCode());
    }

    @Test void evidenceCountUsesTheFullUnsignedIntRangeInDatabasePayloadsAndTypedResponses() {
        for (long evidenceCount : List.of(2147483647L, 2147483648L, 4294967295L)) {
            UserProfileMapper profiles = mock(UserProfileMapper.class);
            ProfileSnapshotReader snapshots = mock(ProfileSnapshotReader.class);
            UserProfile profile = visible("READY", null);
            profile.setProfileDataJson("{\"recentConfusions\":[{\"kpId\":3,\"detail\":\"step unclear\",\"evidenceCount\":"
                    + evidenceCount + ",\"confidence\":0.8,\"lastOccurredAt\":\"2026-07-20T10:00:00Z\"}]}");
            UserKnowledgeMastery mastery = mastery(3L);
            mastery.setEvidenceCount(evidenceCount);
            when(profiles.selectVersionStamp(1001L)).thenReturn(stamp(profile));
            when(snapshots.read(1001L)).thenReturn(new ProfileSnapshot(profile, List.of(mastery)));

            FullProfile response = (FullProfile) service(mock(ProfileActorResolver.class), profiles, snapshots, disabledCache())
                    .profile(1001L, new MockHttpServletRequest());
            assertEquals(evidenceCount, response.recentConfusions().get(0).evidenceCount());
            assertEquals(evidenceCount, response.knowledge().get(0).evidenceCount());
        }
    }

    @Test void evidenceCountRejectsOutOfRangeNegativeAndNonIntegralDatabaseAndCacheValues() throws Exception {
        for (String invalidEvidenceCount : List.of("4294967296", "-1", "1.5")) {
            UserProfileMapper profiles = mock(UserProfileMapper.class);
            ProfileSnapshotReader snapshots = mock(ProfileSnapshotReader.class);
            UserProfile profile = visible("READY", null);
            profile.setProfileDataJson("{\"recentConfusions\":[{\"kpId\":3,\"detail\":\"step unclear\",\"evidenceCount\":"
                    + invalidEvidenceCount + ",\"confidence\":0.8,\"lastOccurredAt\":\"2026-07-20T10:00:00Z\"}]}");
            when(profiles.selectVersionStamp(1001L)).thenReturn(stamp(profile));
            when(snapshots.read(1001L)).thenReturn(new ProfileSnapshot(profile, List.of()));
            assertEquals("PROFILE_SERVICE_DEGRADED", assertThrows(M6ApiException.class,
                    () -> service(mock(ProfileActorResolver.class), profiles, snapshots, disabledCache())
                            .profile(1001L, new MockHttpServletRequest())).getErrorCode());

            ObjectNode cached = cachedFullWithConfusion(visible("READY", null));
            ((ObjectNode) cached.withArray("recentConfusions").get(0)).set("evidenceCount", json.readTree(invalidEvidenceCount));
            assertInvalidFullCacheFallsBack(visible("READY", null), cached);
        }
    }

    @Test void positiveNestedIdentifiersRejectBigIntegersAndDecimalsFromDatabaseAndCache() throws Exception {
        for (String invalidKpId : List.of("9223372036854775808", "1.5")) {
            UserProfileMapper profiles = mock(UserProfileMapper.class);
            ProfileSnapshotReader snapshots = mock(ProfileSnapshotReader.class);
            UserProfile profile = visible("READY", null);
            profile.setRecentFocusJson("[{\"kpId\":" + invalidKpId + ",\"weight\":0.5}]");
            when(profiles.selectVersionStamp(1001L)).thenReturn(stamp(profile));
            when(snapshots.read(1001L)).thenReturn(new ProfileSnapshot(profile, List.of()));
            assertEquals("PROFILE_SERVICE_DEGRADED", assertThrows(M6ApiException.class,
                    () -> service(mock(ProfileActorResolver.class), profiles, snapshots, disabledCache())
                            .profile(1001L, new MockHttpServletRequest())).getErrorCode());

            ObjectNode cached = cachedFull(visible("READY", null));
            ((ObjectNode) cached.withArray("recentFocus").get(0)).set("kpId", json.readTree(invalidKpId));
            assertInvalidFullCacheFallsBack(visible("READY", null), cached);
        }
    }

    @Test void cachePreservesUnsignedIntEvidenceCountsWithoutDatabaseFallback() throws Exception {
        UserProfile row = visible("READY", null);
        for (long evidenceCount : List.of(2147483647L, 2147483648L, 4294967295L)) {
            UserProfileMapper profiles = mock(UserProfileMapper.class);
            ProfileSnapshotReader snapshots = mock(ProfileSnapshotReader.class);
            when(profiles.selectVersionStamp(1001L)).thenReturn(stamp(row));
            ObjectNode cached = cachedFullWithConfusion(row);
            ((ObjectNode) cached.withArray("recentConfusions").get(0)).put("evidenceCount", evidenceCount);
            ((ObjectNode) cached.withArray("knowledge").get(0)).put("evidenceCount", evidenceCount);
            CacheFixture fixture = enabledCache(cacheEnvelope(cached));

            FullProfile hit = (FullProfile) service(mock(ProfileActorResolver.class), profiles, snapshots, fixture.cache())
                    .profile(1001L, new MockHttpServletRequest());
            assertEquals(evidenceCount, hit.recentConfusions().get(0).evidenceCount());
            assertEquals(evidenceCount, hit.knowledge().get(0).evidenceCount());
            verifyNoInteractions(snapshots);
            verify(fixture.redis(), never()).delete(M6ProfileCache.profileKey(1001L));
        }
    }

    @Test void summaryProfileUsesTheSame16383CodePointLimitForDatabaseAndCacheReads() throws Exception {
        String tooLong = "x".repeat(16384);
        UserProfileMapper profiles = mock(UserProfileMapper.class);
        ProfileSnapshotReader snapshots = mock(ProfileSnapshotReader.class);
        UserProfile profile = visible("READY", null);
        profile.setSummaryProfile(tooLong);
        when(profiles.selectVersionStamp(1001L)).thenReturn(stamp(profile));
        when(snapshots.read(1001L)).thenReturn(new ProfileSnapshot(profile, List.of()));
        assertEquals("PROFILE_SERVICE_DEGRADED", assertThrows(M6ApiException.class,
                () -> service(mock(ProfileActorResolver.class), profiles, snapshots, disabledCache())
                        .profile(1001L, new MockHttpServletRequest())).getErrorCode());

        ObjectNode cached = cachedFull(visible("READY", null));
        cached.put("summaryProfile", tooLong);
        assertInvalidFullCacheFallsBack(visible("READY", null), cached);
    }

    @Test void recentFocusWeightIsRequiredAndBoundedByInternalContract() {
        for (String recentFocus : List.of(
                "[{\"kpId\":3,\"weight\":null}]", "[{\"kpId\":3}]",
                "[{\"kpId\":3,\"weight\":-0.01}]", "[{\"kpId\":3,\"weight\":1.01}]")) {
            UserProfileMapper profiles = mock(UserProfileMapper.class);
            ProfileSnapshotReader snapshots = mock(ProfileSnapshotReader.class);
            UserProfile profile = visible("READY", null);
            profile.setRecentFocusJson(recentFocus);
            when(profiles.selectVersionStamp(1001L)).thenReturn(stamp(profile));
            when(snapshots.read(1001L)).thenReturn(new ProfileSnapshot(profile, List.of()));

            M6ApiException error = assertThrows(M6ApiException.class,
                    () -> service(mock(ProfileActorResolver.class), profiles, snapshots, disabledCache())
                            .profile(1001L, new MockHttpServletRequest()));
            assertEquals("PROFILE_SERVICE_DEGRADED", error.getErrorCode());
        }
    }

    @Test void enabledCacheFullHitWithMissingFocusWeightIsDeletedAndFallsBackToDatabase() throws Exception {
        UserProfile row = visible("READY", null);
        ObjectNode cached = cachedFull(row);
        ((ObjectNode) cached.withArray("recentFocus").get(0)).remove("weight");
        assertInvalidFullCacheFallsBack(row, cached);
    }

    @Test void enabledCacheRejectsNullOutOfRangeAndCoercedFocusWeightsButAcceptsLegalWeight() throws Exception {
        UserProfile row = visible("READY", null);
        for (Map.Entry<String, String> badWeight : Map.of(
                "null", "null", "negative", "-0.01", "greater than one", "1.01", "string", "\"0.5\"").entrySet()) {
            ObjectNode cached = cachedFull(row);
            ((ObjectNode) cached.withArray("recentFocus").get(0)).set("weight", json.readTree(badWeight.getValue()));
            assertInvalidFullCacheFallsBack(row, cached);
        }

        UserProfileMapper profiles = mock(UserProfileMapper.class);
        ProfileSnapshotReader snapshots = mock(ProfileSnapshotReader.class);
        when(profiles.selectVersionStamp(1001L)).thenReturn(stamp(row));
        CacheFixture fixture = enabledCache(cacheEnvelope(cachedFull(row)));
        FullProfile hit = (FullProfile) service(mock(ProfileActorResolver.class), profiles, snapshots, fixture.cache())
                .profile(1001L, new MockHttpServletRequest());
        assertEquals(new BigDecimal("0.5"), hit.recentFocus().get(0).weight());
        verifyNoInteractions(snapshots);
        verify(fixture.redis(), never()).delete(M6ProfileCache.profileKey(1001L));
    }

    @Test void enabledCacheRejectsMissingOrNullConfusionFieldsAndDatabaseFailureIsDegraded() throws Exception {
        UserProfile row = visible("READY", null);
        for (String field : List.of("kpId", "detail", "evidenceCount", "confidence", "lastOccurredAt")) {
            ObjectNode missing = cachedFullWithConfusion(row);
            ((ObjectNode) missing.withArray("recentConfusions").get(0)).remove(field);
            assertInvalidFullCacheFallsBack(row, missing);
            ObjectNode nullValue = cachedFullWithConfusion(row);
            ((ObjectNode) nullValue.withArray("recentConfusions").get(0)).putNull(field);
            assertInvalidFullCacheFallsBack(row, nullValue);
        }

        UserProfileMapper profiles = mock(UserProfileMapper.class);
        ProfileSnapshotReader snapshots = mock(ProfileSnapshotReader.class);
        when(profiles.selectVersionStamp(1001L)).thenReturn(stamp(row));
        ObjectNode cached = cachedFull(row);
        ((ObjectNode) cached.withArray("recentFocus").get(0)).put("weight", -0.01);
        CacheFixture fixture = enabledCache(cacheEnvelope(cached));
        M6ApiException error = assertThrows(M6ApiException.class,
                () -> service(mock(ProfileActorResolver.class), profiles, snapshots, fixture.cache())
                        .profile(1001L, new MockHttpServletRequest()));
        assertEquals("PROFILE_SERVICE_DEGRADED", error.getErrorCode());
        verify(fixture.redis()).delete(M6ProfileCache.profileKey(1001L));
    }

    @Test void enabledCacheSummaryHitUsesTheSameFocusWeightContract() throws Exception {
        UserProfile row = visible("READY", null);
        for (String badWeight : List.of("null", "-0.01", "1.01", "\"0.5\"")) {
            ObjectNode cached = cachedLecturingSummary(row);
            ((ObjectNode) cached.withArray("recentFocus").get(0)).set("weight", json.readTree(badWeight));
            UserProfileMapper profiles = mock(UserProfileMapper.class);
            ProfileSnapshotReader snapshots = mock(ProfileSnapshotReader.class);
            when(profiles.selectVersionStamp(1001L)).thenReturn(stamp(row));
            when(snapshots.read(1001L)).thenReturn(new ProfileSnapshot(row, List.of(mastery(3L))));
            CacheFixture fixture = enabledCache(cacheEnvelope(cached));
            SummaryView result = service(mock(ProfileActorResolver.class), profiles, snapshots, fixture.cache())
                    .summary(1001L, "lecturing", null, new MockHttpServletRequest());
            assertInstanceOf(LecturingSummary.class, result);
            verify(fixture.redis()).delete(M6ProfileCache.summaryKey(1001L, "lecturing", null));
            verify(snapshots).read(1001L);
        }
    }

    @Test void summaryCacheBindsCanonicalSceneAndRequestedKnowledgePoint() throws Exception {
        UserProfile row = visible("READY", null);
        UserProfileMapper profiles = mock(UserProfileMapper.class);
        ProfileSnapshotReader snapshots = mock(ProfileSnapshotReader.class);
        when(profiles.selectVersionStamp(1001L)).thenReturn(stamp(row));
        when(snapshots.read(1001L)).thenReturn(new ProfileSnapshot(row, List.of(mastery(3L))));
        CacheFixture mismatched = enabledCache(cacheEnvelope(cachedExplainingSummary(row, 9L)));

        ExplainingSummary fallback = (ExplainingSummary) service(mock(ProfileActorResolver.class), profiles, snapshots, mismatched.cache())
                .summary(1001L, "photo_qa", 3L, new MockHttpServletRequest());

        assertEquals(3L, fallback.knowledgeContext().kpId());
        verify(mismatched.redis()).delete(M6ProfileCache.summaryKey(1001L, "explaining", 3L));
        verify(snapshots).read(1001L);

        UserProfileMapper hitProfiles = mock(UserProfileMapper.class);
        ProfileSnapshotReader hitSnapshots = mock(ProfileSnapshotReader.class);
        when(hitProfiles.selectVersionStamp(1001L)).thenReturn(stamp(row));
        CacheFixture matching = enabledCache(cacheEnvelope(cachedExplainingSummary(row, 3L)));
        ExplainingSummary hit = (ExplainingSummary) service(mock(ProfileActorResolver.class), hitProfiles, hitSnapshots, matching.cache())
                .summary(1001L, "explaining", 3L, new MockHttpServletRequest());
        assertEquals(3L, hit.knowledgeContext().kpId());
        verifyNoInteractions(hitSnapshots);
        verify(matching.redis(), never()).delete(M6ProfileCache.summaryKey(1001L, "explaining", 3L));

        assertSummaryKnowledgeMismatchFallsBack(row, "lecturing", cachedLecturingSummary(row, 9L));
        assertSummaryKnowledgeMismatchFallsBack(row, "conversation", cachedConversationSummary(row, 9L));

        UserProfileMapper unrequestedProfiles = mock(UserProfileMapper.class);
        ProfileSnapshotReader unrequestedSnapshots = mock(ProfileSnapshotReader.class);
        when(unrequestedProfiles.selectVersionStamp(1001L)).thenReturn(stamp(row));
        CacheFixture unrequested = enabledCache(cacheEnvelope(cachedLecturingSummary(row)));
        assertInstanceOf(LecturingSummary.class, service(mock(ProfileActorResolver.class), unrequestedProfiles, unrequestedSnapshots, unrequested.cache())
                .summary(1001L, "lecturing", null, new MockHttpServletRequest()));
        verifyNoInteractions(unrequestedSnapshots);
    }

    @Test void malformedSummaryFocusWeightDeletesOnlyItsSummaryKeyAndFallsBack() throws Exception {
        UserProfile row = visible("READY", null);
        ObjectNode cached = cachedLecturingSummary(row);
        ((ObjectNode) cached.withArray("recentFocus").get(0)).remove("weight");
        UserProfileMapper profiles = mock(UserProfileMapper.class);
        ProfileSnapshotReader snapshots = mock(ProfileSnapshotReader.class);
        when(profiles.selectVersionStamp(1001L)).thenReturn(stamp(row));
        when(snapshots.read(1001L)).thenReturn(new ProfileSnapshot(row, List.of(mastery(3L))));
        CacheFixture fixture = enabledCache(cacheEnvelope(cached));

        assertInstanceOf(LecturingSummary.class, service(mock(ProfileActorResolver.class), profiles, snapshots, fixture.cache())
                .summary(1001L, "lecturing", null, new MockHttpServletRequest()));

        verify(fixture.redis()).delete(M6ProfileCache.summaryKey(1001L, "lecturing", null));
        verify(snapshots).read(1001L);
    }

    @Test void learningPaceIsStrictInDatabaseAndCachedProfiles() throws Exception {
        UserProfile invalidDatabase = visible("READY", null);
        invalidDatabase.setLearningPace("turbo");
        UserProfileMapper databaseProfiles = mock(UserProfileMapper.class);
        ProfileSnapshotReader databaseSnapshots = mock(ProfileSnapshotReader.class);
        when(databaseProfiles.selectVersionStamp(1001L)).thenReturn(stamp(invalidDatabase));
        when(databaseSnapshots.read(1001L)).thenReturn(new ProfileSnapshot(invalidDatabase, List.of()));
        assertEquals("PROFILE_SERVICE_DEGRADED", assertThrows(M6ApiException.class,
                () -> service(mock(ProfileActorResolver.class), databaseProfiles, databaseSnapshots, disabledCache())
                        .profile(1001L, new MockHttpServletRequest())).getErrorCode());

        UserProfile row = visible("READY", null);
        ObjectNode invalidCached = cachedFull(row);
        invalidCached.put("learningPace", "turbo");
        assertInvalidFullCacheFallsBack(row, invalidCached);
    }

    private UserProfileQueryServiceImpl service(ProfileActorResolver actor, UserProfileMapper profiles,
                                                 ProfileSnapshotReader snapshots, M6ProfileCache cache) {
        return new UserProfileQueryServiceImpl(actor, profiles, snapshots, json, cache, new SceneSummaryAssembler(json));
    }
    private M6ProfileCache disabledCache() { return new M6ProfileCache(mock(StringRedisTemplate.class), json, false); }
    private CacheFixture enabledCache(String payload) {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked") ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.get(anyString())).thenReturn(payload);
        return new CacheFixture(redis, new M6ProfileCache(redis, json, true));
    }
    private String cacheEnvelope(JsonNode data) throws Exception {
        ObjectNode envelope = json.createObjectNode();
        envelope.put("cacheSchema", "1.0"); envelope.put("profileVersion", 2L); envelope.put("profileStatus", "READY");
        envelope.putNull("statusReason"); envelope.put("computedAt", "2026-07-20T10:00:00Z"); envelope.set("data", data);
        return json.writeValueAsString(envelope);
    }
    private void assertInvalidFullCacheFallsBack(UserProfile row, ObjectNode cached) throws Exception {
        UserProfileMapper profiles = mock(UserProfileMapper.class);
        ProfileSnapshotReader snapshots = mock(ProfileSnapshotReader.class);
        when(profiles.selectVersionStamp(1001L)).thenReturn(stamp(row));
        when(snapshots.read(1001L)).thenReturn(new ProfileSnapshot(row, List.of(mastery(3L))));
        CacheFixture fixture = enabledCache(cacheEnvelope(cached));
        FullProfile result = (FullProfile) service(mock(ProfileActorResolver.class), profiles, snapshots, fixture.cache())
                .profile(1001L, new MockHttpServletRequest());
        assertEquals(List.of(), result.recentFocus());
        verify(fixture.redis()).delete(M6ProfileCache.profileKey(1001L));
        verify(snapshots).read(1001L);
    }
    private ObjectNode cachedFull(UserProfile row) {
        return (ObjectNode) json.valueToTree(new FullProfile(1001L, "READY", null, 2L,
                null, List.of("text"), null, null, List.of(new RecentFocus(3L, new BigDecimal("0.5"))),
                List.of(), null, null, "algo-1", null, row.getComputedAt().toInstant(java.time.ZoneOffset.UTC), List.of(masteryContext(3L))));
    }
    private ObjectNode cachedFullWithConfusion(UserProfile row) {
        ObjectNode cached = cachedFull(row);
        cached.set("recentConfusions", json.valueToTree(List.of(new RecentConfusion(3L, "step unclear", 2L,
                new BigDecimal("0.8"), java.time.Instant.parse("2026-07-20T10:00:00Z")))));
        return cached;
    }
    private ObjectNode cachedLecturingSummary(UserProfile row) {
        return (ObjectNode) json.valueToTree(new LecturingSummary(1001L, "lecturing", "READY", null, 2L,
                row.getComputedAt().toInstant(java.time.ZoneOffset.UTC), null, null, List.of(),
                List.of(new RecentFocus(3L, new BigDecimal("0.5"))), List.of("text"), null));
    }
    private ObjectNode cachedLecturingSummary(UserProfile row, Long kpId) {
        return (ObjectNode) json.valueToTree(new LecturingSummary(1001L, "lecturing", "READY", null, 2L,
                row.getComputedAt().toInstant(java.time.ZoneOffset.UTC), null, masteryContext(kpId), List.of(),
                List.of(new RecentFocus(kpId, new BigDecimal("0.5"))), List.of("text"), null));
    }
    private ObjectNode cachedConversationSummary(UserProfile row, Long kpId) {
        return (ObjectNode) json.valueToTree(new ConversationSummary(1001L, "conversation", "READY", null, 2L,
                row.getComputedAt().toInstant(java.time.ZoneOffset.UTC), masteryContext(kpId), List.of(), List.of("text"), null));
    }
    private void assertSummaryKnowledgeMismatchFallsBack(UserProfile row, String scene, ObjectNode cached) throws Exception {
        UserProfileMapper profiles = mock(UserProfileMapper.class);
        ProfileSnapshotReader snapshots = mock(ProfileSnapshotReader.class);
        when(profiles.selectVersionStamp(1001L)).thenReturn(stamp(row));
        when(snapshots.read(1001L)).thenReturn(new ProfileSnapshot(row, List.of(mastery(3L))));
        CacheFixture fixture = enabledCache(cacheEnvelope(cached));
        SummaryView fallback = service(mock(ProfileActorResolver.class), profiles, snapshots, fixture.cache())
                .summary(1001L, scene, 3L, new MockHttpServletRequest());
        KnowledgeContext context = fallback instanceof LecturingSummary summary ? summary.knowledgeContext()
                : ((ConversationSummary) fallback).knowledgeContext();
        assertEquals(3L, context.kpId());
        verify(fixture.redis()).delete(M6ProfileCache.summaryKey(1001L, scene, 3L));
        verify(snapshots).read(1001L);
    }
    private ObjectNode cachedExplainingSummary(UserProfile row, Long kpId) {
        return (ObjectNode) json.valueToTree(new ExplainingSummary(1001L, "explaining", "READY", null, 2L,
                row.getComputedAt().toInstant(java.time.ZoneOffset.UTC), null, masteryContext(kpId), List.of(),
                List.of("text"), null, null));
    }
    private KnowledgeContext masteryContext(Long kp) { return new KnowledgeContext(kp, "AVAILABLE", BigDecimal.ONE, "MASTERED", BigDecimal.ONE, null, 1L); }
    private record CacheFixture(StringRedisTemplate redis, M6ProfileCache cache) { }
    private UserProfile visible(String status, String reason) {
        UserProfile p = new UserProfile(); p.setUserId(1001L); p.setProfileStatus(status); p.setStatusReason(reason);
        p.setProfileVersion(2L); p.setComputedAt(LocalDateTime.of(2026,7,20,10,0)); p.setLastEventAt(p.getComputedAt());
        p.setProfileDataJson("{\"recentConfusions\":[]}"); p.setPreferredContentModesJson("[\"text\"]");
        p.setRecentFocusJson("[]"); p.setAlgorithmVersion("algo-1"); return p;
    }
    private UserProfile stamp(UserProfile source) { UserProfile p=new UserProfile();p.setUserId(source.getUserId());p.setProfileVersion(source.getProfileVersion());p.setProfileStatus(source.getProfileStatus());p.setStatusReason(source.getStatusReason());p.setComputedAt(source.getComputedAt());return p; }
    private UserKnowledgeMastery mastery(Long kp) { UserKnowledgeMastery x=new UserKnowledgeMastery();x.setKpId(kp);x.setProfileVersion(2L);x.setMasteryScore(BigDecimal.ONE);x.setConfidence(BigDecimal.ONE);x.setMasteryStatus("MASTERED");x.setEvidenceCount(1L);x.setAlgorithmVersion("algo-1");return x; }
}
