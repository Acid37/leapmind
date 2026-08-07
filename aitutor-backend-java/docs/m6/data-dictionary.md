# M6 data dictionary

Before persisting an event, `occurredAt` is converted to UTC and truncated to millisecond precision. Its resulting value must be within the MySQL `DATETIME(3)` interval `1000-01-01T00:00:00.000Z` through `9999-12-31T23:59:59.999Z`; an input whose UTC conversion falls outside that interval is rejected as `400 PROFILE_EVENT_INVALID` and is never written.

`contractVersion` is `1.0`. Events use the ten frozen event types, `schemaVersion=1.0`, strict data allowlists, and a UTF-8 canonical data limit of 16 KiB. Production account deletion/retention and foreign-key policy remain an external approval gate; the development migrations intentionally contain no foreign keys.

Java and Python use internal HTTP `POST /api/internal/ai/build-profile`; credentials and the deployment base URL are external configuration. This repository intentionally contains no Python client, scheduler, algorithm, or profile writer.

Canonical compatibility mappings are `mastery_level` → `mastery_score`, `sample_count` → `evidence_count`, profile `calculated_at` → `computed_at`, and mastery `calculated_at` → `updated_at`. Only the canonical names are persisted and emitted. `photo_qa` is a deprecated input alias for `explaining`; it uses the same authorization, fields and cache key. High-entropy UUID/ULID event IDs are recommended for producers because `event_id` is globally unique; controlled legacy identifiers remain accepted by schema 1.0.

## Redis cache operation

M6 Redis caching is disabled by default with `leapmind.m6.cache.enabled=false`. Enable it only when a reachable Redis service has been configured by deployment configuration; no application sample or secret-bearing configuration is changed by this module. A disabled cache, a Redis read/write failure, or an invalid cache envelope is treated as a cache miss: profile and summary reads fall back to MySQL, and cache failures never turn a successful database read into an error. Redis failures emit a sanitized WARN containing only operation, cache type, exception class and request ID when available; cache keys, profile data, values and exception messages are never logged. Profile entries use `user:profile:{userId}` with a 30-minute TTL; scene summaries use `user:profile:summary:{userId}:{sceneType}:{kpId}` with a 10-minute TTL. Successful profile writers must invalidate affected keys as part of their update workflow. Alert routing and cache hit-rate metrics are deployment integration work items.

## Batch persistence failures

Each batch item is inserted in its own `REQUIRES_NEW` transaction. A systemic `DataAccessException` is a request-level `503 PROFILE_SERVICE_DEGRADED`, not an item-level parameter failure. Items already committed before that failure remain committed and are not rolled back; later items are not attempted.

## Shared-table migration alignment

Upstream already owns Flyway V3 for review-reminder tables, so these M6 tables are introduced by V4–V6. Deployment environments may nevertheless already contain same-named shared tables with a different contract. The migrations deliberately use ordinary `CREATE TABLE`: `IF NOT EXISTS` would only suppress a naming conflict and would not validate an existing table's columns, constraints, or indexes. Before integration, compare `SHOW CREATE TABLE` and `INFORMATION_SCHEMA` with the intended contract and inspect `flyway_schema_history`. A conflicting old physical table must fail quickly; resolve it only with an approved V7-or-later forward migration, without rewriting historical V1–V6 migrations.
