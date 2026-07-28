# Rank-Forge v0.6.6 - Idempotency and duplicate prevention

## 1. Purpose and implementation boundary

`v0.6.6` prevents duplicate unresolved local sync-queue work for the five existing cloud operation types. It follows `v0.6.5.1` and does not add a remote operation log, migration, background work, or queue cleanup.

## 2. Deterministic operation identity

Each queue operation has a deterministic identity composed only from existing local values:

* the `SyncQueueOperationType`; and
* the nullable tournament ID supplied to that operation.

The stable serialized key includes the operation type plus a length-prefixed tournament-ID value, so it distinguishes a null ID from an empty or differently sized ID. It does not include timestamps, attempt counts, failure details, or other changing values.

This identity applies to tournament upload, tournament restoration, draft-match sync, finalized-match sync, and match restoration. Each operation is a tournament-scoped snapshot/action in the current contracts, so the operation type and tournament ID are the complete existing local identifiers available for safe queue deduplication.

## 3. Local queue duplicate prevention

Before recording a non-completed result, the Room queue repository finds the oldest unresolved entry with the same operation identity. Unresolved means every current status except `COMPLETED`:

* `PENDING`;
* `BLOCKED_NETWORK`;
* `BLOCKED_AUTHENTICATION`;
* `FAILED_VALIDATION`;
* `FAILED_AUTHORIZATION`;
* `FAILED_LOCAL`; and
* `FAILED_UNKNOWN`.

When one exists, the repository preserves its row ID, creation time, and attempt count, and deterministically updates only status and failure metadata. It does not insert another row. The oldest entry is selected by creation time and then row ID. A process-local mutex makes the lookup/update-or-insert sequence serial within the app process.

Completed rows remain retained. A direct successful operation completes its matching unresolved entry without inserting a row; completed rows are not active duplicate candidates, so a later unresolved request can create a new active entry without deleting or mutating historical completed work. This version does not retrospectively consolidate any duplicates that may already exist.

## 4. Retry compatibility

`v0.6.5.1` retry execution continues to mutate the selected existing entry only: each attempt increments once, success marks that entry `COMPLETED`, and failure updates its status/failure metadata. The retry executor uses no-record operation actions and does not call queue enqueueing.

If pre-existing duplicate unresolved rows are supplied to a retry batch, the foreground coordinator executes only the oldest eligible row for each operation identity, ordered by creation time and row ID. It leaves later duplicate rows unchanged; retrospective queue cleanup remains outside this version.

## 5. Remote-operation boundary

Existing tournament upload, draft-match sync, and finalized-match sync retain their current deterministic-ID upsert behavior. Restoration actions read cloud data and apply existing local restore/replace behavior; they do not create remote rows.

This version adds no server-side operation ID, uniqueness constraint, RPC, or transaction that could guarantee exactly-once processing across a client crash during a multi-request remote operation. The local queue prevents duplicate unresolved work in one app process; stronger cross-process/server idempotency remains deferred until it can be approved with the necessary backend design.

## 6. Explicit deferrals

The following remain outside `v0.6.6`:

* Supabase migrations, constraints, indexes, functions, policies, RPCs, and storage changes;
* queue schema changes, queue cleanup, and deletion policy;
* WorkManager, services, alarms, scheduled retry, and background processing;
* revision-based conflict detection and resolution; and
* protected finalization, corrections, and audit history.
