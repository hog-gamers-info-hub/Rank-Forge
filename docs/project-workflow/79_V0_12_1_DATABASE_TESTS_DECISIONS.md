# Phase 12 v0.12.1 — Database Tests Decisions

## Status

**Approved for implementation after this decision document is merged.**

## Version

**Phase 12 — Quality Assurance and Security Validation**

**v0.12.1 — Database Tests**

Canonical scope:

> Test Room operations, migrations, transactions, and data-integrity constraints.

---

## 1. Purpose

Complete meaningful direct Room/database test coverage for the existing local persistence implementation.

v0.12.1 covers:

- Room DAO operations
- Room migration correctness
- transaction atomicity
- rollback behavior where realistically reproducible
- foreign-key and cascade integrity
- database-backed ordering/filtering behavior
- duplicate-prevention behavior implemented directly by Room/database constraints

This version does not redesign the database.

---

## 2. Current Room Baseline

The current Room database is:

`app/src/main/java/com/hoggamers/rankforge/data/local/RankForgeDatabase.kt`

Current database version:

`8`

Registered migration chain:

```text
1 → 2
2 → 3
3 → 4
4 → 5
5 → 6
6 → 7
7 → 8
```

The database currently registers 16 entities covering:

- tournaments
- team slots
- roster players
- matches
- placements
- kills
- draft/correction state
- synchronization state
- synchronization queue
- screenshot metadata
- roster screenshot metadata
- OCR evidence
- related local workflow state

Schema export is enabled.

No Room callback is currently registered.

---

## 3. Core Decision

v0.12.1 is expected to be a **test-only Room/database version**.

No production files are approved for planned modification.

The approved implementation boundary is:

- modify 3 existing Android instrumentation/database test files
- create 1 new Android instrumentation/database test file
- modify no production Room code
- modify no entities
- modify no DAOs
- modify no migrations
- modify no database version
- modify no repositories
- modify no Gradle configuration

If implementation discovers a genuine production database defect or an undefined database contract, implementation must stop before modifying production code.

---

## 4. Test Quality Principle

Tests must protect material database behavior.

Do not add tests merely to increase test count.

A database test is justified when it protects behavior such as:

- SQL filtering
- SQL ordering
- Room upsert/replace behavior
- transaction atomicity
- rollback after a genuine later-write failure
- foreign-key enforcement
- cascade behavior
- composite primary-key enforcement
- migration preservation
- final-schema invariants
- synchronization queue ordering/filtering
- revision increment behavior
- persisted state replacement

Do not broadly create one test for every non-null column or primary key if the same contract is already adequately proven.

---

## 5. Existing Coverage Considered Complete

The focused v0.12.1 inventory found adequate existing direct coverage for:

- tournament CRUD
- team-slot CRUD
- roster-player CRUD
- match CRUD
- placement persistence
- kill persistence
- draft persistence
- correction persistence
- screenshot metadata CRUD
- screenshot replacement/update/delete behavior
- match screenshot cascade
- OCR evidence persistence
- OCR evidence ordering
- OCR evidence duplicate rejection
- OCR evidence rollback
- OCR evidence match cascade
- finalized-match multi-table persistence
- correction multi-table persistence
- team-name rollback
- roster replacement rollback
- match finalization rollback
- core tournament foreign-key enforcement
- core team-slot foreign-key enforcement
- core roster-player foreign-key enforcement
- core match foreign-key enforcement
- reopen/persistence behavior already covered by repository/database tests
- each individual migration from 1→2 through 7→8

Do not duplicate these tests without a concrete uncovered contract.

---

## 6. Approved Existing Test Files to Modify

### 1. RankForgeDatabaseMigrationTest.kt

Modify:

```text
app/src/androidTest/java/com/hoggamers/rankforge/data/local/RankForgeDatabaseMigrationTest.kt
```

Add:

- one complete sequential migration from database version 1 through version 8
- preserved representative legacy data assertions after reaching version 8
- representative final-schema/table/index/foreign-key assertions where materially useful
- additional newer-child cascade assertions only where not already directly covered

The purpose is to prove that the complete supported migration chain works as a single path.

Existing individual migration tests remain authoritative.

Do not change migration implementation.

---

### 2. RosterScreenshotMetadataDaoTest.kt

Modify:

```text
app/src/androidTest/java/com/hoggamers/rankforge/data/local/RosterScreenshotMetadataDaoTest.kt
```

Add direct coverage proving:

- deleting a tournament cascades correctly to its `roster_screenshot_metadata` rows

Use the actual current Room foreign-key contract.

Do not change entity or DAO behavior.

---

### 3. RoomTournamentRepositoryTest.kt

Modify:

```text
app/src/androidTest/java/com/hoggamers/rankforge/data/tournament/RoomTournamentRepositoryTest.kt
```

Add only material missing transaction/rollback tests for repository workflows where:

1. multiple Room writes occur in one transaction, and
2. an actual later-write failure can be triggered deterministically using the current test infrastructure.

Candidate areas identified by the inventory include:

- tournament cloud restore/replacement
- match replacement
- draft match creation
- placement writes
- kill writes
- draft-value writes
- clear workflows

However, do **not** add artificial failure hooks or production changes merely to force rollback.

If a candidate workflow cannot naturally produce a controlled later-write failure using current production APIs/test infrastructure, leave it out and document that it is not practically testable within the approved boundary.

The objective is meaningful atomicity coverage, not synthetic failure injection.

---

## 7. Approved New Test File

Create:

```text
app/src/androidTest/java/com/hoggamers/rankforge/data/local/CoreRoomDaoTest.kt
```

This file provides direct Room coverage for currently under-tested core DAOs.

### RankForgeStateDao

Test materially applicable behavior such as:

- initial read when no state exists
- save/insert
- replacement/update behavior
- persisted reread

Follow the actual DAO API.

### SyncRevisionDao

Test materially applicable behavior such as:

- missing revision lookup
- initial upsert
- existing revision replacement/upsert
- revision increment behavior
- persisted reread

Follow the current SQL/DAO contract exactly.

### SyncQueueDao

Test actual Room behavior for:

- insert/enqueue
- deterministic queue ordering
- unresolved/pending filtering
- tournament-specific filtering
- nullable tournament matching where supported by the DAO
- status updates
- attempt-count updates/increments
- deletion/removal behavior
- persisted reread

Do not reproduce JVM fake-DAO tests. These tests specifically verify the real Room SQL behavior.

---

## 8. Migration Decision

The current registered migration chain is:

```text
1 → 2 → 3 → 4 → 5 → 6 → 7 → 8
```

Existing tests already exercise each individual migration.

v0.12.1 adds one complete earliest-supported-to-current migration:

```text
1 → 8
```

The full-chain test must verify representative preserved data after all migrations complete.

It should also verify representative final schema invariants where practical.

No current migration performs material column backfill/default transformation requiring a separate backfill test.

Therefore broad default/backfill testing is not required.

---

## 9. Transaction Decision

v0.12.1 must distinguish between:

- meaningful rollback coverage
- artificial failure injection

Existing repository transaction tests already cover several important workflows.

Additional rollback tests should be added only where the current test infrastructure can trigger a real later-write failure without changing production code.

Do not:

- add production failure hooks
- expose internal transaction functions solely for tests
- modify DAOs to create artificial failures
- weaken database constraints
- create unrealistic invalid states solely to claim transaction coverage

If no safe failure path exists for a candidate workflow, that specific rollback scenario may remain untested in this version.

---

## 10. Integrity Decision

v0.12.1 must directly verify only material Room/database constraints that are currently under-tested.

Required additional integrity coverage includes:

- tournament cascade into `roster_screenshot_metadata`
- actual Room behavior for sync queue persistence
- actual Room behavior for sync revision persistence
- composite-key or duplicate-prevention behavior where directly relevant to newly tested DAOs

Do not create exhaustive duplicate/non-null tests for every table when existing schema/migration/repository tests already adequately protect those contracts.

The Room schema currently has no unique indices beyond primary-key-based uniqueness.

`sha256` indices are non-unique and must not be treated as database uniqueness constraints.

---

## 11. Production File Boundary

No production file is approved for modification.

Do not modify:

```text
app/src/main/java/com/hoggamers/rankforge/data/local/
app/src/main/java/com/hoggamers/rankforge/data/tournament/
```

or any other production directory.

Production source may be read to understand APIs and current SQL/Room contracts.

If a correct test requires a production change, stop and report:

1. exact file
2. exact reason
3. whether the issue is:
   - production defect
   - undefined database contract
   - testability limitation

No production change may proceed under the existing v0.12.1 approval.

---

## 12. Out of Scope

v0.12.1 does not include:

- Supabase schema tests
- RLS
- backend authorization
- cloud synchronization integration
- Edge Functions
- authentication
- Compose UI
- navigation
- OCR algorithms
- scoring
- team matching
- CSV export
- Google Sheets
- device compatibility matrix
- real network failure testing
- full offline recovery workflows
- security review
- genuine screenshot acceptance
- CI configuration
- dependency upgrades
- unrelated refactoring

These belong to other Phase 12 versions.

---

## 13. Approved Implementation File Boundary

### Existing files to modify

```text
app/src/androidTest/java/com/hoggamers/rankforge/data/local/RankForgeDatabaseMigrationTest.kt
app/src/androidTest/java/com/hoggamers/rankforge/data/local/RosterScreenshotMetadataDaoTest.kt
app/src/androidTest/java/com/hoggamers/rankforge/data/tournament/RoomTournamentRepositoryTest.kt
```

### New files to create

```text
app/src/androidTest/java/com/hoggamers/rankforge/data/local/CoreRoomDaoTest.kt
```

### Production files

```text
None.
```

If implementation requires any existing file outside this list, stop before editing it and review the boundary.

---

## 14. Verification Policy

Because v0.12.1 uses Android instrumentation Room tests, JVM-only verification is insufficient.

After implementation, run:

```powershell
.\gradlew.bat assembleDebug
.\gradlew.bat assembleDebugAndroidTest
.\gradlew.bat connectedDebugAndroidTest
git diff --check
```

If focused instrumentation execution is practical, run the directly affected database test classes first before the full connected suite.

No Supabase, Docker, Deno, or backend verification is required.

---

## 15. Completion Criteria

v0.12.1 is complete when:

1. this decision document is merged
2. implementation starts from synchronized `main`
3. only the approved 4 database-test files are changed/created
4. the complete 1→8 migration path is directly tested
5. representative migrated data survives the full chain
6. roster screenshot tournament cascade is directly tested
7. `RankForgeStateDao` has direct Room coverage
8. `SyncRevisionDao` has direct Room coverage
9. `SyncQueueDao` has direct Room coverage
10. only reproducible and meaningful missing transaction rollback tests are added
11. no production file is modified
12. Android test APK compilation succeeds
13. connected instrumentation tests pass
14. `git diff --check` passes
15. implementation is merged through a PR
16. local `main` is synchronized with `origin/main`
17. working tree is clean

---

## 16. Decision Summary

Approved planned implementation:

```text
Room/database test completion only

3 existing instrumentation test files modified
1 new instrumentation test file created
0 production files modified

Full Room migration 1→8 coverage
Roster screenshot cascade coverage
Direct core DAO coverage
Only realistic transaction rollback coverage
No Supabase/backend scope
No production database changes
```

After this document is reviewed and merged, implementation may proceed on:

```text
feature/v0.12.1-database-tests
```
