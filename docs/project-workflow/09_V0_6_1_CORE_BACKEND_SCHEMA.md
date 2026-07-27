# v0.6.1 - Core Backend Database Schema

## 1. Purpose

This document is the implementation and review authority for `v0.6.1 - Core backend database schema`.

`v0.6.1` creates the first versioned Supabase PostgreSQL schema migration for authenticated tournament data. It establishes only the initial relational foundation required by the approved tournament, roster, match, and result model.

This is a documentation and decision gate. It does not create SQL, migrations, RLS policies, Android code, Room changes, synchronization, or production database changes.

## 2. Governing authorities

This document is governed by:

* `AGENTS.md`
* `docs/project-workflow/00_PHASE_AND_VERSION_ROADMAP.md`
* `docs/project-workflow/05_PHASE_6_KICKOFF_AUDIT.md`
* `docs/project-workflow/06_PHASE_6_DECISIONS.md`
* `docs/project-workflow/08_V0_6_0_1_SESSION_AUTH_HARDENING.md`
* `docs/02_SYSTEM_ARCHITECTURE.md`
* `docs/03_DATABASE_DESIGN.md`
* `docs/07_SUPABASE_BACKEND.md`
* `docs/09_TESTING_AND_ACCEPTANCE.md`
* `docs/11_SECURITY_AND_PRIVACY.md`
* Relevant documents under `docs/ai/`

Current explicit user decisions and this approved task govern the immediate v0.6.1 scope and file boundaries.

## 3. Dependency on completed v0.6.0 and v0.6.0.1

`v0.6.0` and `v0.6.0.1` are complete and merged on `main` in `ab3a4b3`.

`v0.6.0` introduced additive email/password Supabase authentication while preserving local-first tournament workflows. `v0.6.0.1` hardened persisted-session restoration, current-device logout, typed authentication failures, and authentication UI verification.

This version depends on Supabase Auth as the identity source, but it must not add Android cloud upload, cloud restoration, synchronization, ownership behavior, or any later Phase 6 capability.

## 4. Current backend baseline

The verified repository baseline contains Supabase scaffolding only:

* `supabase/config.toml` is present and exposes the configured `public` and `graphql_public` schemas.
* `supabase/migrations/` contains no production migration; only `.gitkeep` is present.
* `supabase/tests/` contains no database tests; only `.gitkeep` is present.
* `supabase/functions/` contains no Edge Function implementation; only `.gitkeep` is present.
* No tracked production PostgreSQL schema, RLS policy set, storage bucket definition, synchronization implementation, or cloud-data workflow has been verified.

The Android authentication client is already implemented, but it does not establish the v0.6.1 database schema and must remain unchanged by the future schema implementation.

## 5. Approved v0.6.1 decisions

### Version boundary

`v0.6.1` creates only the first core Supabase PostgreSQL schema migration for authenticated tournament data.

It must not implement:

* Synchronization
* RLS ownership policies
* Storage
* Edge Functions or RPCs
* Android cloud upload
* Android cloud restoration
* Conflict handling
* Idempotency
* Finalized-data protection workflows

### Core table set

The exact initial core table set is:

* `tournaments`
* `tournament_team_slots`
* `players`
* `matches`
* `match_results`

No additional v0.6.1 table is approved.

### Ownership root

Supabase Auth is the identity source. `tournaments` must contain:

```text
owner_id uuid not null references auth.users(id)
```

Child records derive ownership through their parent tournament hierarchy. Detailed ownership policies are deferred to `v0.6.2`.

### Identity generation

All v0.6.1 core tables use UUID primary keys. UUID generation must occur in PostgreSQL through a standard PostgreSQL/Supabase-supported function verified against the installed local Supabase environment during implementation.

Android-generated IDs must not become the permanent backend identity source in v0.6.1.

### Status and timestamps

Status fields are text columns. Conservative check constraints may cover only required draft, finalized, or completion boundaries needed for coherent schema shape.

PostgreSQL enum types are not approved for v0.6.1 unless a verified implementation blocker proves they are required.

Every core table includes `created_at` and `updated_at` as timezone-aware timestamps. Trigger-based timestamp automation is not approved unless explicitly justified and verified during implementation.

## 6. Exact implementation scope

### Allowed in v0.6.1 implementation

* One new versioned Supabase migration for the core backend schema
* The five approved core tables only
* UUID primary keys and database-side UUID generation
* `tournaments.owner_id` referencing `auth.users(id)`
* Parent-child foreign keys for the core hierarchy
* Required domain columns for tournament, team-slot, player, match, and result data
* `created_at` and `updated_at` timestamp columns on every core table
* Minimal text status fields
* Essential schema-coherence constraints
* RLS enabled on every exposed core table
* Local Supabase migration verification
* Accurate implementation and verification evidence added to this document after implementation

### Essential constraint scope

Only these constraint categories are allowed:

* Primary keys
* Required non-null fields
* Essential foreign keys
* Basic status checks
* Basic numeric range checks needed to prevent invalid core data shape, such as invalid placements or negative kills

## 7. Explicit exclusions

The following are excluded from v0.6.1:

* Android code changes
* Room schema, entities, DAOs, or migrations
* Tournament upload or restoration
* Match synchronization
* Offline sync queues
* WorkManager
* Idempotency implementation
* Conflict detection or resolution
* Revision or concurrency fields unless an explicitly approved deferred placeholder is unavoidable
* Detailed uniqueness constraints
* Production indexing strategy
* Full RLS ownership policies
* Cross-owner authorization tests
* Storage buckets and storage policies
* Screenshot, OCR, correction-history, audit, export, standing-cache, notification, profile, collaboration, analytics, or sync-queue tables
* Edge Functions, RPCs, or other privileged functions
* Triggers unless explicitly justified
* PostgreSQL enum types unless explicitly justified
* Destructive SQL or production database changes
* Secrets or local configuration
* Broad documentation edits outside this authority file
* `v0.6.1.1` or later functionality

## 8. Proposed core table set

The following table set is approved for the first migration:

| Table | Purpose | Parent relationship |
| --- | --- | --- |
| `tournaments` | Authenticated tournament root and owner reference | References `auth.users(id)` through `owner_id` |
| `tournament_team_slots` | The fixed team-slot records belonging to a tournament | References `tournaments(id)` |
| `players` | Structured player records belonging to a tournament team slot | References `tournament_team_slots(id)` |
| `matches` | Match metadata and minimal draft/finalized state belonging to a tournament | References `tournaments(id)` |
| `match_results` | Team result inputs belonging to a match | References `matches(id)` and `tournament_team_slots(id)` |

The migration must not add tables for screenshots, OCR observations, corrections, synchronization, exports, standings caches, profiles, collaboration, audit logs, notifications, or analytics.

## 9. Approved ownership model

Ownership is rooted at `tournaments.owner_id` and supplied by Supabase Auth.

The approved hierarchy is:

```text
auth.users
  └── tournaments.owner_id
        ├── tournament_team_slots.tournament_id
        │     └── players.tournament_team_slot_id
        ├── matches.tournament_id
        └── match_results.match_id
              └── match_results.tournament_team_slot_id
```

Child rows do not receive an independent owner model in v0.6.1. Their eventual authorization derives through the parent tournament. Full ownership policies, cross-account denial, and authorization tests are deferred to `v0.6.2` and `v0.6.2.1`.

The schema implementation must not silently claim, upload, reassign, or delete existing Room tournaments.

## 10. Approved identity strategy

Every v0.6.1 core table uses a UUID primary key generated on the database side through a standard PostgreSQL/Supabase-supported function.

The implementation must verify the selected UUID-generation function against the installed local Supabase environment before relying on it. The documentation gate does not choose an unsupported or unverified function name.

Foreign keys use the UUID primary keys of their parent records. Android-local identifiers may be retained as local implementation details but are not the permanent backend identity source for v0.6.1.

No separate profile table, player-login identity, team-owner identity, collaboration identity, or public-user identity is introduced.

## 11. Approved column categories

The migration may represent the following categories only:

### Common columns on every core table

* `id`: UUID primary key
* `created_at`: timezone-aware timestamp, required
* `updated_at`: timezone-aware timestamp, required

### Tournament columns

* `owner_id`: UUID, required, foreign key to `auth.users(id)`
* Tournament name
* Tournament date
* Organizer details required by the existing logical design
* Minimal tournament status text

### Team-slot columns

* `tournament_id`: required parent foreign key
* Slot number required to represent the fixed 1-to-12 tournament structure
* Team name and other required structured team fields
* Minimal completion or validation state only where needed for schema coherence

### Player columns

* `tournament_team_slot_id`: required parent foreign key
* Player display name
* Normalized player value only if required by the approved logical design and core duplicate/matching shape

### Match columns

* `tournament_id`: required parent foreign key
* Match number
* Match date
* Map name or map field
* Minimal draft/finalized status text

### Match-result columns

* `match_id`: required parent foreign key
* `tournament_team_slot_id`: required parent foreign key
* Placement value with the minimal approved numeric range
* Kill value with a non-negative numeric boundary

Source classification, OCR observations, correction history, revisions, finalization actors, synchronization state, idempotency keys, export state, standings projections, and audit metadata are deferred unless a later approved version adds their tables or fields.

## 12. Constraint boundary for v0.6.1 versus v0.6.1.1

### v0.6.1

The first migration may establish only the constraints required to make the five-table schema coherent and prevent invalid core data shape:

* Primary keys
* Required non-null fields
* Essential parent-child foreign keys
* Basic status checks
* Basic placement and non-negative numeric checks

Detailed uniqueness constraints, comprehensive roster and result cardinality constraints, production indexing, revision/concurrency fields, advanced finalized protections, and expanded schema tests are deferred to `v0.6.1.1`.

The v0.6.1 migration must not imply that deferred constraints are already enforced.

### v0.6.1.1

The follow-up version is the boundary for constraints, indexes, revisions, and schema tests that require additional design or could affect synchronization, concurrency, finalized data, or production query strategy.

## 13. RLS boundary for v0.6.1 versus v0.6.2

### v0.6.1

RLS must be enabled on every exposed v0.6.1 table.

Full ownership policies are not implemented in this version. If tables are created in the exposed `public` schema, the implementation must avoid accidental row exposure before ownership policies are approved. With no approved allow policies, the safe default is deny-by-policy until the later ownership implementation is ready.

No authentication-only policy is acceptable as a substitute for ownership authorization.

### v0.6.2 and v0.6.2.1

`v0.6.2` defines and implements ownership-based RLS policies through the tournament hierarchy. `v0.6.2.1` adds cross-owner authorization and security tests for `SELECT`, `INSERT`, `UPDATE`, and `DELETE`, including update `USING` and `WITH CHECK` behavior where required.

## 14. Migration requirements

Future implementation must:

* Create one new versioned migration file for the five core tables.
* Inspect the local Supabase environment and existing migration history before creating schema objects.
* Never edit, delete, reorder, or rewrite an applied migration.
* Avoid destructive SQL.
* Avoid production database changes without explicit approval, backup, rollback instructions, and separate deployment authorization.
* Verify the migration locally before any review or later application.
* Keep the migration reproducible and limited to this document's approved table and constraint boundary.

The migration implementation must not add seeds, secrets, local credentials, functions, policies, or storage objects outside the approved v0.6.1 boundary.

## 15. Verification requirements

Future implementation must run and report:

```powershell
supabase --version
supabase migration list
supabase db reset
supabase migration list
git diff --check
git status --short
```

The implementation must also verify:

* Only the five approved core tables exist after local reset.
* Every core table has a UUID primary key.
* `tournaments.owner_id` references `auth.users(id)`.
* Child foreign keys point to the approved parent hierarchy.
* Required domain columns and timestamps are present.
* RLS is enabled on every exposed core table.
* No full ownership policy implementation was introduced.
* No excluded table, migration, configuration, function, storage object, Android change, Room change, or generated artifact was added.

If local Supabase requires Docker and Docker is not running, verification is blocked and the exact command output must be reported. Any schema tests added only because the existing local workflow requires them must remain minimal and within the v0.6.1 boundary.

### Acceptance criteria

1. A new versioned Supabase migration creates only the five approved core tables.
2. Every core table has a UUID primary key.
3. `tournaments.owner_id` references `auth.users(id)`.
4. Child tables reference the correct parent table.
5. Required core tournament, team-slot, player, match, and match-result fields are represented.
6. `created_at` and `updated_at` exist on every core table.
7. RLS is enabled on every exposed core table.
8. Full ownership policy implementation is not introduced before `v0.6.2`.
9. No Android, Room, sync, WorkManager, storage, Edge Function, OCR, export, or later-phase table is introduced.
10. No existing migration is edited or deleted.
11. Local migration verification passes.
12. Secret and local-configuration scans pass.
13. Implementation and verification evidence is updated in this document after implementation.
14. The work is independently reversible through the v0.6.1 migration and documentation change.

## 16. Security requirements

The v0.6.1 implementation must confirm:

* Android remains limited to approved public/client configuration.
* Service-role keys, database passwords, JWT secrets, private credentials, and tokens are absent from the repository and migration.
* No `.env`, `local.properties`, `supabase/.temp/`, generated build output, or local secret is added.
* Every exposed table has RLS enabled.
* No authentication-only ownership bypass is introduced.
* No row data is accidentally exposed before ownership policies are approved.
* No privileged function, storage object, sync operation, or production data operation is introduced.
* Migration history remains reproducible and non-destructive.

Full ownership authorization review belongs to `v0.6.2` and cross-account security verification belongs to `v0.6.2.1`.

## 17. Rollback boundary

The v0.6.1 change is independently reversible through the documentation change and the single new versioned migration, subject to local verification and explicit database rollback planning.

Applied production migrations must not be edited or deleted. If a later corrective action is required, it must use a new corrective migration with explicit approval, backup, impact analysis, and rollback instructions.

This version must not require Android rollback, Room migration rollback, synchronization rollback, storage cleanup, secret rotation, or production data deletion because those systems are outside scope.

## 18. Implementation readiness decision

Ready to implement v0.6.1 after this documentation gate is reviewed, committed, merged, and main is confirmed clean and synchronized.

This readiness statement does not authorize SQL application, production deployment, RLS policy implementation, synchronization, or any later version.

## 19. Required next branch

```text
feature/v0.6.1-core-backend-schema
```
