# Rank-Forge v0.6.5.1 - Retry, connectivity, and restart recovery

## 1. Purpose and gate status

This document is the decision gate for `v0.6.5.1 - Retry, connectivity, and restart recovery`. It defines the approved implementation boundary after the persistent offline sync queue delivered in `v0.6.5`.

Required implementation branch:

```text
feature/v0.6.5.1-sync-retry-recovery
```

Decision: ready for a narrowly scoped implementation task. This gate does not implement retry behavior, change the queue schema, or authorize Supabase changes.

## 2. Roadmap boundary

`v0.6.5.1` follows `v0.6.5 - Persistent offline sync queue` and is limited to foreground retry, best-effort foreground connectivity handling, and recovery of the persisted queue after application startup.

It precedes and does not implement the later work for idempotency and duplicate prevention, revision-based conflict detection and resolution, protected finalization, and protected corrections/audit history.

## 3. Approved retry model

`v0.6.5.1` uses a foreground-only retry coordinator over the existing persisted Room sync queue.

Retry work may run only while the application process is active and foregrounded. It may be initiated by an eligible foreground action, a best-effort foreground connectivity event, or foreground app-start/session-restoration recovery.

The version must not add WorkManager, a background worker, scheduled job, alarm, service, periodic task, or any other background processing.

## 4. Connectivity boundary

Connectivity handling is best-effort and foreground-only. A foreground connectivity indication may trigger retry only while the app is active/foregrounded.

The implementation must not monitor connectivity in the background, register a mechanism intended to replay work while the app is closed, or claim guaranteed delivery from a connectivity signal. Existing failure classification remains the basis for determining whether an entry is retryable.

## 5. Restart and session-restoration recovery

The Room sync queue remains persisted across application restart. During app start or session restoration, the foreground application may inspect that persisted queue and retry eligible entries when the user has a valid authenticated session and other foreground conditions are valid.

For this version, restart recovery means both of the following:

* pending queue state survives restart; and
* eligible entries may be retried after startup by the foreground application.

It does not mean that work replays while the app is closed or that a background component continues retrying after the process is gone. Entries blocked by authentication remain retained until a valid signed-in session exists.

## 6. Retry eligibility

Only these existing `v0.6.5` queue statuses are eligible for retry:

| Queue status | v0.6.5.1 handling |
| --- | --- |
| `BLOCKED_NETWORK` | Retry when foreground conditions indicate network access is available. |
| `BLOCKED_AUTHENTICATION` | Retry only when a valid signed-in session exists. |

The following statuses are not retryable in this version: `PENDING`, `FAILED_VALIDATION`, `FAILED_AUTHORIZATION`, `FAILED_LOCAL`, `FAILED_UNKNOWN`, and `COMPLETED`.

The coordinator must not reinterpret a non-retryable failure as a network or authentication failure merely to retry it.

## 7. Operation eligibility

All five existing `v0.6.5` operation types are eligible when their queue status is retryable:

* tournament upload;
* tournament restoration;
* draft match synchronization;
* finalized match synchronization; and
* match restoration.

No new queue operation type, queue abstraction, or queue redesign is authorized by this decision.

## 8. Attempts, completion, and failure state

Each retry attempt must increment the entry's `attemptCount` once.

On successful retry, the entry must be marked `COMPLETED`. Completed entries are retained in `v0.6.5.1`; deletion and cleanup policy are deferred.

On failed retry, the entry must update its status and failure metadata deterministically using the existing failure-classification model. A retry must preserve the distinction between network, authentication, validation, authorization, local, and unknown failure outcomes rather than collapsing them into a generic result.

## 9. UI boundary

This decision authorizes no queue-management screen, retry button, notification, or new recovery-specific UI state. Existing v0.6.5 action feedback remains the baseline.

Any future UI that exposes retry or recovery state must be approved in a separate, scoped task. The coordinator behavior itself must remain testable without placing queue processing in composables.

## 10. Required future verification

The implementation task for this decision must include:

* unit tests for retry-status eligibility, valid-session gating, all five eligible operation types, attempt-count increments, deterministic failed-retry metadata, and completed-entry retention;
* tests for foreground app-start/session-restoration inspection of persisted entries, including that no invalid-session retry occurs;
* relevant connected-device tests for foreground connectivity recovery and restart recovery while the app is reopened, without relying on background execution;
* manual device verification of blocked-network retry after foreground connectivity returns, blocked-authentication retry after a valid session is restored, non-retryable status preservation, and retained completed entries;
* the focused Android verification selected by the implementation task, plus `git diff --check`.

## 11. Explicit exclusions and deferred work

The following are explicitly deferred beyond `v0.6.5.1`:

* scheduled backoff and exponential backoff;
* WorkManager, background replay, and all other background processing;
* cleanup or deletion of completed queue entries;
* idempotency and duplicate prevention;
* conflict detection, conflict resolution, and revision strategy;
* protected finalization and protected corrections/audit history;
* Supabase migrations, functions, policies, RPCs, and storage changes; and
* queue schema redesign, unless a concrete compile or test blocker proves that a minimal migration is necessary and receives a separate decision.

## 12. Decision status

No unresolved product decision remains within this gate. Future implementation must preserve the existing `v0.6.5` queue contracts and stop for a separate decision if the approved foreground-only scope cannot be met without a wider schema, backend, or architecture change.
