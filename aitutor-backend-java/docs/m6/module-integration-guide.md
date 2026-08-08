# M1--M8 integration guide

| Module | Current responsibility | Owner / status |
| --- | --- | --- |
| M1 | answer/practice events; practice context | module owner; typed contract defined, default port disabled; production not wired |
| M2 | explanation events; explaining context | module owner; typed contract defined, default port disabled; production not wired |
| M3 | weak-point events | module owner; typed contract defined, default port disabled; production not wired |
| M4 | lecture interaction events; lecturing context | module owner; not connected |
| M5 | material-used events; lesson-prep shape | module owner; not connected, activation pending |
| M6 | profile facts and review/preference events | Zhang Zihong; no writer/engine loop |
| M7 | doubt events; conversation context | module owner; typed contract defined, default port disabled; production not wired |
| M8 | policy-authorized scene-context read only; no new event production | M8 owner; typed contract defined, default port disabled; production not wired |

| Module | Concrete next step / owner |
| --- | --- |
| M1 | approve M1 principal and submit answer/practice command mapping / M1 owner |
| M2 | approve explanation command and read policy / M2 owner |
| M3 | approve weak-point fact producer / M3 owner |
| M4 | approve lecture command and scene policy / M4 owner |
| M5 | approve lesson-prep reader activation / M5 owner |
| M6 | approve projection writer and profile reader / M6 owner |
| M7 | approve doubt privacy review and conversation reader / M7 owner |
| M8 | issue trusted principal and capability policy for the existing read-only scene contract; do not add event production / M8 owner |

Java callers construct a typed `LearningEventCommand` and pass a trusted internal context; Java consumers call `ProfileContextProvider` directly in the same application. They must not HTTP-call their own controller. Every real module integration, trusted principal issuance, event-id choice, and kp mapping remains the module owner's separately approved task.

```java
EventPublishContext access = new EventPublishContext(userId, ActorKind.USER, userId,
    null, SourceModule.M1, Purpose.PUBLISH_LEARNING_EVENT, requestId);
LearningEventCommand command = new LearningEventCommand("evt-1", userId, Instant.now(), "session-1",
    new KnowledgePointRef.Resolved(kpId), "trace-1", new LearningEventPayload.AnswerQuestion(true, 2, 10, 0, null));
EventPublishOutcome result = publisher.publish(access, command); // default: NOT_CONFIGURED; explicitly allowed disabled adapter: NOT_CONNECTED
ProfileAccessContext readAccess = new ProfileAccessContext(userId, ActorKind.USER, userId,
    null, SourceModule.M1, Purpose.READ_SCENE_CONTEXT, requestId);
KnowledgeStatusContext context = provider.getKnowledgeStatus(readAccess,
    new KnowledgeStatusRequest(List.of(new KnowledgePointRef.Resolved(kpId))));
```

The ports are in-process only: do not call internal HTTP, tables, or caches directly. Each module owner owns command construction, capability-policy registration, and handling disabled results.

`user_events` is the only future profile fact source. `event_collections` and `/api/events/**` remain deprecated compatibility behavior; no dual write or cutover occurs here. No client payload may carry profile summaries, raw conversation data, credentials, or arbitrary event JSON. Before Java release, run the Java contract tests; reading profile context remains disabled until the engine/writer and authorization are approved.

The v1 engine machine contract is [`profile-engine-contract.yaml`](profile-engine-contract.yaml). Java client, Python route, scheduler, writer, shared persistence/read cores, policy-enabled adapters, and the CAS loop are not connected or included in this increment. `modelVersion` and `promptVersion` are future Python/lead-confirmed fields, not v1 additions.

M8 may request only policy-authorized scene context and must not publish a new M6 event type. Unresolved knowledge-point keys must be rejected before persistence or engine IO. Python connectivity, real service identities, a KP registry, Flyway history, outbox/CAS, and the end-to-end production path remain NO-GO until a later reviewed activation PR.
