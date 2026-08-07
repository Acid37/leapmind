# M6 platform integration ADR

## Decision

This increment adds only disabled Java ports. `user_events` is the sole future profile-event fact source. Legacy `/api/events/**` and `event_collections` remain deprecated compatibility sinks: paths/tables are retained, user calls are self-only, and operations that need an absent service identity fail closed. There is no double write, migration, backfill, cutover, or hash/batch semantic change.

`publishBatch` is an internal 1..100 convenience that returns one outcome per command. Its disabled implementation makes no database or HTTP call and offers no transaction, atomicity, outbox, or delivery guarantee; these remain explicit unresolved decisions.

Public actors are self-only. Internal USER actors bind actor and subject; SERVICE actors use typed principal/source/purpose data and the default capability policy denies every combination. The ports do not trust browser-provided source, principal, profile summary, or arbitrary JSON.

## Open decisions

The transaction policy (same-transaction event write versus durable outbox), CAS writer, cache invalidation, service identity issuance, and M1--M8 activation require later approval. Schema `1.0` versus an unresolved-kp-compatible schema `1.1` is also unresolved; this increment persists neither. Python route/client/writer connection is explicitly absent.

## Consequences

`NOT_CONNECTED`/`NOT_CONFIGURED` are real outcomes, never fabricated `READY`. No database, HTTP, scheduler, writer, event-state advance, or production configuration is introduced.
