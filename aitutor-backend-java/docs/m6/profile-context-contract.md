# Profile context contract

The provider is internal, typed, and read-only: the frontend cannot submit `summaryText` or any generic map. Every response includes the common identity/status/version/timestamps/confidence/availability envelope plus scene data.

Enabled-provider cache keys are `userId + sceneType + resolved kp ids + profileVersion`; TTL/stale windows belong to that provider's configuration. `STALE` carries materialized older data; `NOT_READY`, `DEGRADED`, `NOT_CONNECTED`, and `NOT_CONFIGURED` carry no profile facts.

| Scene / consumer | Request | Typed fields |
| --- | --- | --- |
| practice / M1 | 1--100 distinct resolved kp IDs | knowledge statuses, weak points, learning pace, content modes |
| explaining / M2 | one resolved kp ID | grade, knowledge context, recent confusions, explanation style, pace, summary text |
| lecturing / M4 | none or resolved kp ID | weak points, recent focus, pace, content modes, summary text |
| conversation / M7 | none or resolved kp ID | knowledge context, recent confusions, explanation style, summary text |
| lesson_prep / M5 | zero or resolved kp IDs | weak points, grade, content modes, pace, suggestions, summary text |

`getKnowledgeStatus` accepts `KnowledgeStatusRequest` with 1--100 distinct resolved knowledge-point IDs and returns an ordered typed list. Default policy returns `NOT_CONFIGURED`; explicitly allowed disabled mode returns an empty `NOT_CONNECTED` result.

Lists are never null and text is bounded. Consumers should cache only a versioned response envelope and treat it as advisory: `NOT_READY` has no profile, `STALE` should be rendered with a freshness warning, `DEGRADED` should suppress personalization, and `NOT_CONNECTED` means no profile integration. No production adapter may manufacture `READY` facts.

A future enabled provider must measure every materialized scene, full-profile, and knowledge response as deterministic UTF-8 JSON and enforce a 64 KiB hard limit. A serialization failure or over-limit representation must return a no-facts `DEGRADED` response instead of an oversized payload. This increment includes only the disabled provider, which performs zero IO.

M5 Python `/api/lesson-prep` is not connected and must not accept a browser-provided profile summary as a substitute for this provider.
