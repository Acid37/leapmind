# Profile projection CAS runbook

Projection is disabled. The repository currently has duplicate Flyway versions V3--V6 and target environments may have unknown `flyway_schema_history`; do not infer safety from this skeleton.

Do not invent an outbox, transaction, or replay policy during an incident. Capture the watermark and profile version, then escalate an atomic-delivery decision for architecture approval.

Allowed preflight is read-only and performed by an approved operator:

```sql
SELECT version, description, type, success FROM flyway_schema_history ORDER BY installed_rank;
SHOW CREATE TABLE user_events;
SHOW CREATE TABLE user_profiles;
SHOW CREATE TABLE user_knowledge_mastery;
```

Forbidden here: `flyway migrate`, `repair`, `baseline`, migration renumbering, history edits, schema changes, backfill, deletes, or Python table initialization.

Future approved flow: read validated `user_events` at a stable watermark; call the v1 engine; validate identity/version/status response; in one transaction CAS profile+mastery+watermark/event state and write an outbox row; invalidate cache only after commit. A CAS miss, replay mismatch, unavailable dependency, or validation failure rolls back and leaves event state unchanged. Validate rollback with a real database, concurrent CAS contention, lost response retry, and cache-after-commit tests before activation. `NO_CHANGE` must not overwrite persisted `computedAt`.
