# Phase 12 v0.12.2 — Backend Tests Decisions

## Status

**Approved for implementation after this decision document is merged.**

## Version

**Phase 12 — Quality Assurance and Security Validation**

**v0.12.2 — Backend Tests**

Canonical scope:

> Test Supabase schema, RLS, authorization, synchronization, and idempotency.

---

## 1. Purpose

Complete meaningful backend test coverage for the existing Rank Forge Supabase implementation.

v0.12.2 covers:

- PostgreSQL schema and integrity constraints
- Row Level Security
- backend authorization
- synchronization RPC behavior
- revision and stale-write protection
- finalized-data protection
- backend idempotency and retry behavior
- screenshot Storage and metadata access control
- correction-audit authorization
- export-operation relational integrity

This is a **test-completion version**.

It does not redesign the backend or add new backend security mechanisms.

---

## 2. Current Backend Baseline

Relevant backend migrations include:

- `20260727163228_v0_6_1_core_backend_schema.sql`
- `20260727165522_v0_6_1_1_schema_hardening.sql`
- `20260727172504_v0_6_2_rls_ownership_policies.sql`
- `20260728120000_v0_6_7_revision_safe_writes.sql`
- `20260728150000_v0_6_8_protected_match_finalization.sql`
- `20260728160000_v0_6_8_1_protected_corrections_audit.sql`
- `20260729110000_v0_7_5_match_screenshot_storage.sql`
- `20260729120000_v0_7_6_match_screenshot_metadata.sql`
- `20260801120000_v0_10_7_export_retry_idempotency.sql`
- `20260801140000_v0_10_8_export_verification.sql`

Material backend tables include:

- `tournaments`
- `tournament_team_slots`
- `players`
- `matches`
- `match_results`
- `match_correction_audit_entries`
- `match_screenshot_metadata`
- `export_operations`

Relevant Storage resource:

- private bucket `match-screenshots`

Relevant backend functions/RPCs include:

- tournament snapshot-write RPCs
- match snapshot-write RPCs
- `finalize_match_snapshot`
- `correct_finalized_match_snapshot`
- export-operation state RPCs
- export verified-success reconciliation

Existing backend test suites already provide extensive pgTAP and Deno coverage.

v0.12.2 adds only remaining material gaps.

---

## 3. Core Decision

v0.12.2 is expected to be a **test-only backend version**.

No production migration, function, RLS policy, Edge Function, schema, or Android file is approved for modification.

Approved planned implementation:

- modify 7 existing pgTAP test files
- create no new test files
- modify no Deno tests
- modify no production backend files

If a new test exposes an actual production defect, implementation must stop and report it instead of silently changing backend behavior.

---

## 4. Existing Coverage Considered Complete

No material new v0.12.2 coverage is currently required for:

- core owner CRUD isolation on the five primary backend tables
- cross-account core table read/write denial
- ownership reassignment denial
- anonymous core-table denial
- core child-table parent ownership boundaries
- Edge bearer-token authorization
- Edge fail-closed authorization behavior
- correction revision advancement
- stale correction rejection
- draft-match correction rejection
- duplicate finalized correction protection
- export logical-identity uniqueness
- export claim/replay behavior
- export lease handling
- retryable export failure
- uncertain export outcome handling
- stale lease protection
- cross-account export-operation reuse denial
- verified-success reconciliation
- export success metadata validation

Do not duplicate these contracts merely to increase test count.

---

## 5. Approved Existing Test Files to Modify

### 1. Schema Structure

Modify:

`supabase/tests/01_v0_6_1_1_schema_structure.sql`

Add explicit structural coverage for:

- `matches.finalized_by`
- its foreign-key relationship to the intended authentication user table

Do not redesign the schema.

---

### 2. Schema Constraints

Modify:

`supabase/tests/02_v0_6_1_1_schema_constraints.sql`

Add enforcement coverage proving:

- an invalid `matches.finalized_by` reference is rejected according to the existing foreign-key contract

Do not introduce new constraints.

---

### 3. Protected Match Finalization

Modify:

`supabase/tests/05_v0_6_8_protected_match_finalization.sql`

Add runtime coverage for the existing synchronization/finalization contract.

Material cases include:

- initial tournament snapshot write
- legitimate tournament snapshot update
- initial or legitimate match snapshot write/update as supported
- revision advancement
- stale revision rejection
- cross-account snapshot rejection
- successful finalization
- finalization validation failure where already defined
- finalized-match overwrite rejection
- finalization retry/idempotency behavior where already implemented
- wrong-user finalization rejection
- anonymous finalization rejection
- direct mutation attempts against protected finalized data where current RLS/grants prohibit them

Use existing RPC contracts exactly.

Do not invent new response states or error codes.

---

### 4. Protected Corrections Audit

Modify:

`supabase/tests/06_v0_6_8_1_protected_corrections_audit.sql`

Add material missing coverage for:

- correction-audit table foreign keys
- correction-audit delete behavior according to existing FK rules
- owner read access
- cross-account read denial
- anonymous read denial
- authenticated-client direct insert/update/delete denial where current grants/RLS prohibit them
- wrong-user correction RPC rejection
- anonymous correction RPC rejection
- cross-account correction isolation

Preserve existing correction revision/idempotency tests.

---

## 6. Frozen Decision — Audit Row Immutability

For v0.12.2, “immutable correction-audit rows” means:

> Ordinary authenticated application clients cannot directly mutate or delete audit rows under the current backend grants/RLS contract.

v0.12.2 does **not** define or require a stronger database-owner/service-role append-only invariant.

The existing backend does not currently contain a trigger or equivalent database-level mechanism preventing privileged service-role/database-owner mutation.

Therefore this version must not add:

- append-only triggers
- new privilege architecture
- service-role mutation restrictions
- new database enforcement mechanisms

A stronger invariant, if desired, must be reviewed separately as security hardening, most appropriately during v0.12.7 or a separately approved defect/hardening version.

Tests in v0.12.2 must verify the existing client authorization contract only.

---

## 7. Screenshot Storage RLS

Modify:

`supabase/tests/07_v0_7_5_match_screenshot_storage.sql`

Add runtime access-control cases for the existing private screenshot Storage contract.

Test materially applicable behavior for:

- owner access
- cross-account denial
- anonymous denial

Cover operations only where current Storage policies grant or deny those operations.

Do not alter Storage policies.

Do not add new Storage behavior.

---

## 8. Screenshot Metadata

Modify:

`supabase/tests/08_v0_7_6_match_screenshot_metadata.sql`

Add runtime coverage for:

- owner access
- cross-account denial
- anonymous denial
- direct mutation authorization where applicable
- delete cascade from the relevant parent record according to the existing FK contract

Do not alter screenshot metadata policies or constraints.

---

## 9. Export Operation Integrity

Modify:

`supabase/tests/90_v0_10_7_export_retry_idempotency.sql`

The export state machine and idempotency behavior are already considered adequately tested.

Add only missing relational-integrity coverage for:

- export-operation foreign-key enforcement
- export-operation parent deletion/cascade behavior according to the existing schema

Do not expand or rewrite the export idempotency state-machine suite.

---

## 10. Synchronization Decision

v0.12.2 must provide runtime backend coverage for the synchronization semantics already implemented by snapshot RPCs.

Required material coverage includes, where supported by the existing functions:

- initial snapshot write
- legitimate update
- revision advancement
- stale revision rejection
- cross-account rejection
- finalized-data overwrite rejection
- finalization success
- finalization validation
- safe retry/idempotency

No new restoration RPC is required.

There is no separate backend restoration/readback RPC in the current contract.

Android restoration and local queue behavior are outside v0.12.2.

---

## 11. Authorization Decision

Runtime tests should prove existing authorization instead of relying only on function definitions or grant inspection.

Material missing cases include:

- wrong-user privileged RPC invocation
- anonymous privileged RPC invocation
- correction-audit cross-account isolation
- screenshot metadata cross-account isolation
- screenshot Storage cross-account isolation
- direct finalized-data mutation denial
- direct correction-audit mutation denial

Tests must use existing roles, grants, RLS policies, and RPC behavior.

Do not redesign authorization.

---

## 12. Idempotency Decision

Existing export-operation idempotency coverage is already strong and should not be duplicated.

Existing correction duplicate protection is also substantially covered.

Additional v0.12.2 work should focus only on missing runtime cases such as:

- snapshot retry behavior where an explicit current idempotent/revision-safe contract exists
- finalization retry behavior where currently supported
- authorization around duplicate correction attempts

Do not invent generalized idempotency semantics for RPCs that do not currently define them.

---

## 13. Production File Boundary

No production files are approved for modification.

Do not modify:

- `supabase/migrations/`
- production files under `supabase/functions/`
- `supabase/config.toml`
- Android production code
- Room code
- Gradle configuration

Production backend files may be read to understand existing contracts.

If a correct test reveals a genuine defect, stop and report:

1. exact production file
2. exact failing contract
3. whether it is:
   - production defect
   - undefined contract
   - testability limitation

No production fix may proceed under the existing approval.

---

## 14. Out of Scope

v0.12.2 does not include:

- Room/database tests
- Android local sync queue tests
- Compose/UI testing
- navigation
- OCR
- scoring
- matching
- device compatibility
- generic offline recovery
- genuine screenshot acceptance
- broad repository secret scanning
- full security hardening
- production migration redesign
- new backend features
- new synchronization APIs
- new Storage behavior
- Google API work
- CI configuration
- dependency upgrades
- live Supabase testing

These belong to other Phase 12 versions.

---

## 15. Approved Implementation File Boundary

### Existing test files to modify

```text
supabase/tests/01_v0_6_1_1_schema_structure.sql
supabase/tests/02_v0_6_1_1_schema_constraints.sql
supabase/tests/05_v0_6_8_protected_match_finalization.sql
supabase/tests/06_v0_6_8_1_protected_corrections_audit.sql
supabase/tests/07_v0_7_5_match_screenshot_storage.sql
supabase/tests/08_v0_7_6_match_screenshot_metadata.sql
supabase/tests/90_v0_10_7_export_retry_idempotency.sql
```

### New test files

`None.`

### Deno files

`None.`

### Production files

`None.`

If implementation requires any existing file outside this list, stop before editing it and review the boundary.

---

## 16. Local Verification Policy

All backend verification must remain local.

Repository-pinned Supabase CLI version:

`2.109.1`

Required verification after implementation:

```powershell
npx --yes supabase@2.109.1 db reset
npx --yes supabase@2.109.1 test db
git diff --check
```

If the local environment uses `npx.cmd`, the equivalent commands are acceptable.

Docker Desktop/local Supabase services may be required.

Do not:

- link a production project
- run remote SQL
- push migrations
- deploy functions
- modify secrets
- test against production data

No Deno test execution is required because no Deno files are approved for change.

---

## 17. Completion Criteria

v0.12.2 is complete when:

1. this decision document is merged
2. implementation begins from synchronized `main`
3. only the approved 7 pgTAP files are modified
4. `matches.finalized_by` FK structure and enforcement are tested
5. correction-audit FK and client authorization are tested
6. screenshot Storage runtime RLS is tested
7. screenshot metadata runtime RLS and cascade behavior are tested
8. snapshot synchronization RPC runtime behavior is tested
9. stale revision rejection is tested
10. cross-account snapshot rejection is tested
11. finalization runtime authorization/protection is tested
12. correction wrong-user/anonymous behavior is tested
13. export-operation FK/cascade behavior is tested
14. no production backend file is modified
15. local Supabase database reset succeeds
16. pgTAP suite passes
17. `git diff --check` passes
18. implementation is merged through a PR
19. local `main` is synchronized with `origin/main`
20. working tree is clean

---

## 18. Decision Summary

Approved planned implementation:

```text
Backend test completion only

7 existing pgTAP files modified
0 new test files
0 Deno files modified
0 production files modified

Schema/FK enforcement coverage
Runtime RLS coverage
Runtime authorization coverage
Snapshot synchronization coverage
Finalization protection coverage
Correction-audit access coverage
Storage RLS coverage
Export relational-integrity coverage

No live Supabase changes
No production hardening
No backend redesign
```

After this document is reviewed and merged, implementation may proceed on:

`feature/v0.12.2-backend-tests`
