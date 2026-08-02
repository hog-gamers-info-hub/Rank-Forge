# Phase 12 v0.12.6 — Offline and Recovery Testing Decisions

## Status

**Approved for test-only implementation.**

## Version

**Phase 12 — Quality Assurance and Security Validation**

**v0.12.6 — Offline and Recovery Testing**

Canonical scope:

> Test connectivity loss, interrupted sync, app restart, and retry behavior.

---

## 1. Purpose

v0.12.6 verifies that Rank-Forge remains safe and recoverable when cloud synchronization cannot complete normally.

The required failure/recovery conditions are:

1. connectivity loss
2. interrupted sync execution
3. application/database restart
4. retry after recovery

This version is primarily verification of already implemented Phase 5, Phase 6, and Phase 11 behavior.

It must not redesign the synchronization architecture.

---

## 2. Existing Production Foundation

Earlier versions already implemented:

- Room-backed local-first persistence
- offline tournament workflow
- persistent Room sync queue
- foreground sync retry coordination
- foreground connectivity monitoring
- authenticated session-restoration recovery
- retry execution for all existing sync-operation types
- retry attempt counting
- retry failure-state persistence
- duplicate unresolved-operation prevention
- revision/conflict protection
- finalized-match protection
- Phase 11 local-first cloud-sync workflow integration

v0.12.6 must verify those mechanisms under explicit failure/recovery sequences.

It must not reimplement them.

---

## 3. Existing Sync Operation Types

The current persistent queue supports:

```text
TOURNAMENT_UPLOAD
TOURNAMENT_RESTORATION
DRAFT_MATCH_SYNC
FINALIZED_MATCH_SYNC
MATCH_RESTORATION
```

v0.12.6 does not add a new operation type.

Existing retry dispatch coverage for all five operation types remains authoritative and must continue to pass.

---

## 4. Connectivity-Loss Decision

Connectivity loss must not trigger a cloud retry while the foreground network is unavailable.

Required sequence:

```text
authenticated session
-> network unavailable
-> no recovery attempt
-> network becomes available
-> foreground recovery triggers
```

The test must prove that:

- unavailable connectivity does not trigger recovery
- restored connectivity does trigger recovery
- authentication requirements remain respected
- network transitions do not mutate queue identity
- failure isolation remains best-effort

No Android connectivity production implementation change is authorized.

---

## 5. Interrupted-Sync Decision

An interrupted retry must not silently lose the queued operation.

For testing purposes, interruption means a retry executor throws or otherwise stops before recording a successful completion.

Required invariant:

```text
eligible queued entry
-> retry starts
-> attempt count advances
-> execution is interrupted
-> entry is not marked completed
-> entry remains retryable
-> later recovery can retry the same operation
```

The second retry must use the same queued operation identity.

The implementation must not create a replacement queue row simply because the previous retry was interrupted.

The test must not weaken cancellation semantics.

`CancellationException` must continue to propagate where production behavior requires it.

---

## 6. App-Restart Decision

Retryable queue state must survive a real Room database close and reopen.

The Android Room test must verify persistence of at least:

- queue entry ID
- operation type
- tournament ID
- retryable status
- failure category
- attempt count
- created timestamp/ordering identity where relevant

Required sequence:

```text
persist retryable queue entry
-> update retry attempt/failure state
-> close Room database
-> reopen same database
-> reread queue
-> exact unresolved queue state remains available
```

This represents the persistent state required for app-restart recovery.

No Room entity, DAO, migration, schema version, or database configuration change is planned.

---

## 7. Session-Restoration Recovery Decision

Existing authenticated foreground-recovery behavior must continue to use persisted eligible queue entries after session restoration.

Existing coverage already proves that restored authentication invokes foreground queue recovery.

v0.12.6 should strengthen interruption/recovery behavior rather than duplicate basic authentication tests.

A failed queue-recovery attempt must not corrupt or replace the authenticated session state.

---

## 8. Retry Decision

Retry must remain deterministic.

Required behavior:

### Successful retry

```text
BLOCKED_NETWORK
-> retry
-> attempt count increments once
-> existing operation executes
-> same queue entry becomes COMPLETED
```

### Retry failure

```text
BLOCKED_NETWORK
-> retry
-> attempt count increments once
-> failure occurs
-> same queue entry receives deterministic failure/retry state
-> no duplicate queue entry
```

### Interrupted retry followed by later recovery

```text
BLOCKED_NETWORK
-> attempt 1 interrupted
-> unresolved entry retained
-> later recovery
-> attempt 2 succeeds
-> same entry becomes COMPLETED
```

No blind duplicate upload may be introduced.

---

## 9. Non-Retryable State Protection

Existing non-retryable queue states must remain non-retryable according to current policy.

v0.12.6 must not reinterpret:

- validation failures
- authorization failures
- local persistence failures
- conflict failures
- completed operations

as generic network retries.

Conflict behavior remains governed by existing Phase 6 rules.

---

## 10. Finalized Data Safety

Offline/recovery testing must not weaken finalized-match protection.

A queued or interrupted finalized-match sync must not:

- reopen a finalized match
- make finalized result fields editable
- route finalized data through draft mutation paths
- duplicate finalized results
- bypass protected correction behavior

Existing Phase 11 regression coverage remains part of the overall verification suite.

No production finalized-state changes are authorized.

---

## 11. Exact Approved Implementation Boundary

Modify only:

```text
app/src/androidTest/java/com/hoggamers/rankforge/data/local/CoreRoomDaoTest.kt

app/src/test/java/com/hoggamers/rankforge/domain/sync/ForegroundSyncQueueRetryCoordinatorTest.kt

app/src/test/java/com/hoggamers/rankforge/domain/sync/RecoverForegroundSyncQueueUseCaseTest.kt

app/src/test/java/com/hoggamers/rankforge/domain/sync/RecoverSyncQueueOnForegroundConnectivityUseCaseTest.kt
```

No new files are planned.

If a production file or any file outside this boundary appears necessary, stop before editing and report the reason.

---

## 12. CoreRoomDaoTest Requirements

Add focused real-Room coverage proving a retryable queue entry survives database reopen.

The test should:

1. insert a retryable sync queue entry
2. preserve an exact operation identity
3. increment attempt state
4. preserve/update failure metadata
5. close the database
6. reopen the same database
7. reread the queue
8. assert the exact unresolved entry remains

Do not alter database production code.

---

## 13. ForegroundSyncQueueRetryCoordinatorTest Requirements

Add focused recovery coverage for:

```text
first retry -> interrupted/fails before completion
second retry -> success
```

The test must verify:

- the first attempt increments once
- interruption does not mark the operation completed
- the entry remains eligible when its existing retryable state permits it
- the second attempt uses the same entry ID
- cumulative attempt count is correct
- successful second attempt marks the existing entry completed
- no enqueue/new-row path is used

Existing retry-success, failure-metadata, duplicate identity, and policy tests must remain unchanged and passing.

---

## 14. RecoverForegroundSyncQueueUseCaseTest Requirements

Strengthen recovery isolation around interrupted retry execution.

Verify that:

- an exception during queue retry does not escape into authenticated foreground/session recovery
- the queued item is not falsely marked completed
- retryable state remains available for a later recovery attempt
- a later recovery attempt can complete the same queued item
- the authentication/session recovery contract is not altered

Do not add UI behavior.

---

## 15. RecoverSyncQueueOnForegroundConnectivityUseCaseTest Requirements

Add an explicit connectivity transition test:

```text
network unavailable
-> no recovery

network available
-> recovery
```

Use the same authenticated session context.

Verify the recovery call count exactly.

Also preserve existing behavior that:

- signed-out sessions do not retry
- unavailable network does not retry
- recovery failure is isolated from the connectivity signal

---

## 16. Existing Tests to Reuse Without Modification

Existing tests already provide important supporting coverage and should be rerun, not duplicated unnecessarily:

- `RoomPersistentSyncQueueRepositoryTest`
- `QueueOperationRetryExecutorTest`
- `RecordSyncQueueOutcomeTest`
- `AuthViewModelTest`
- Phase 11 cloud synchronization workflow tests

In particular, existing coverage already verifies:

- duplicate unresolved-entry prevention
- all five retry-operation mappings
- no-record retry paths
- retry status mapping
- restored authenticated session triggers queue recovery
- local workflow remains usable while sync is queued
- finalized local review remains protected during queued sync

---

## 17. Explicit Out of Scope

Do not modify or introduce:

- production sync algorithms
- sync queue entities
- SyncQueueDao
- Room database version
- Room migrations
- WorkManager
- Android services
- alarms
- background jobs
- new connectivity observers
- network libraries
- Supabase schema
- Supabase migrations
- RLS
- RPCs
- Edge Functions
- authentication implementation
- session-storage implementation
- sync operation identity
- idempotency rules
- conflict resolution algorithms
- finalized protection
- correction/audit rules
- scoring
- standings
- OCR
- screenshots
- export
- UI/navigation
- Gradle dependencies
- AndroidManifest permissions

---

## 18. Implementation Classification

Planned implementation:

```text
Test-only
```

Production files:

```text
0
```

Existing test files modified:

```text
4
```

New files:

```text
0
```

Supabase changes:

```text
0
```

Room schema changes:

```text
0
```

Gradle changes:

```text
0
```

---

## 19. Verification

### Focused JVM tests

Run:

```powershell
.\gradlew.bat testDebugUnitTest `
  --tests "com.hoggamers.rankforge.domain.sync.ForegroundSyncQueueRetryCoordinatorTest" `
  --tests "com.hoggamers.rankforge.domain.sync.RecoverForegroundSyncQueueUseCaseTest" `
  --tests "com.hoggamers.rankforge.domain.sync.RecoverSyncQueueOnForegroundConnectivityUseCaseTest" `
  --tests "com.hoggamers.rankforge.domain.sync.QueueOperationRetryExecutorTest" `
  --tests "com.hoggamers.rankforge.domain.sync.RecordSyncQueueOutcomeTest" `
  --tests "com.hoggamers.rankforge.data.sync.RoomPersistentSyncQueueRepositoryTest" `
  --tests "com.hoggamers.rankforge.presentation.auth.AuthViewModelTest"
```

### Android Room persistence test

With one Android target connected:

```powershell
.\gradlew.bat assembleDebugAndroidTest

.\gradlew.bat :app:connectedDebugAndroidTest `
  -P "android.testInstrumentationRunnerArguments.class=com.hoggamers.rankforge.data.local.CoreRoomDaoTest"
```

### Full regression

After focused verification:

```powershell
.\gradlew.bat testDebugUnitTest
.\gradlew.bat assembleDebug assembleDebugAndroidTest
.\gradlew.bat connectedDebugAndroidTest
```

### Scope verification

```powershell
git diff --check
git diff --name-only
```

Only the four approved test files may be modified.

---

## 20. Failure Classification

If a new test fails, classify it before modifying production behavior.

Possible classifications:

1. incorrect test setup
2. existing test-helper limitation
3. expected non-retryable policy behavior
4. genuine queue-persistence defect
5. genuine interrupted-retry defect
6. genuine connectivity-recovery defect
7. genuine session/app-restart recovery defect

Only classifications 4–7 may justify a separate production patch.

Do not silently expand v0.12.6.

---

## 21. Acceptance Criteria

v0.12.6 is accepted when:

1. connectivity loss does not trigger retry
2. connectivity restoration triggers foreground retry when authenticated
3. retryable queue state survives Room close/reopen
4. interrupted retry does not falsely complete or delete queued work
5. later retry can recover the same operation
6. attempt count remains deterministic
7. retry does not create duplicate operation identity
8. authenticated foreground/session recovery remains isolated from retry failure
9. all five existing operation retry mappings remain passing
10. non-retryable states remain protected
11. finalized-data protection remains intact through regression coverage
12. no production files are required
13. focused tests pass
14. full JVM suite passes
15. full connected Android suite passes
16. `git diff --check` passes
17. only the four approved test files change
18. implementation is merged through PR
19. `main` is synchronized

---

## 22. Final Decision

**v0.12.6 is approved as a focused test-only verification version.**

The intended test matrix is:

```text
connectivity loss
-> no retry

connectivity restoration
-> retry

sync interruption
-> unresolved work retained

Room/app restart
-> unresolved work restored

later retry
-> same operation completes
```

No new synchronization architecture is required unless testing exposes a genuine production defect.
