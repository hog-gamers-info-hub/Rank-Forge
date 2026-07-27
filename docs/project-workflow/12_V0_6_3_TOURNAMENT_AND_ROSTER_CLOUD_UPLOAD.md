# Rank-Forge v0.6.3 - Tournament and roster cloud upload

## 1. Purpose and gate status

This document is the decision gate for `v0.6.3 - Tournament and roster cloud upload`. It defines the approved implementation boundary before Android upload code is written.

Required future implementation branch:

```text
feature/v0.6.3-tournament-roster-cloud-upload
```

Decision: ready for implementation after this document is reviewed and the implementation branch is confirmed. This gate does not implement upload behavior, change the roadmap, or authorize production Supabase changes.

## 2. Roadmap boundary

The roadmap explicitly sequences this version before:

* `v0.6.3.1` - tournament and roster cloud restoration;
* `v0.6.4` - draft match synchronization;
* `v0.6.5` - persistent offline sync queue;
* `v0.6.6` - idempotency and duplicate prevention;
* `v0.6.7` and `v0.6.7.1` - revision-based conflict detection and resolution;
* `v0.6.8` and `v0.6.8.1` - protected finalization, corrections, and audit history.

Therefore v0.6.3 is a bounded upload-copy milestone, not general synchronization or restoration.

## 3. Approved upload scope

The upload unit is one explicitly selected local tournament and its current local roster snapshot. It includes:

* tournament metadata: name, date, organizer name, organizer contact, and the local draft-level status mapping;
* all twelve fixed tournament team slots, including empty team names where the local model has them;
* roster players for each local team slot, including display name and the existing normalized name representation.

The status mapping is explicit: local `DRAFT` and local `CONFIRMED` both upload as cloud `tournaments.status = 'draft'`; every uploaded team slot uses cloud `status = 'draft'`. Cloud status transitions are not part of this version, and the mapping does not claim that a local confirmed roster is a finalized cloud tournament.

Match rows and match-result rows are excluded. The roadmap assigns match synchronization to `v0.6.4` and later, so v0.6.3 must not upload `matches`, `match_results`, placements, kills, draft values, corrections, standings, or derived scoring.

Upload is an explicit backup/copy action for the selected tournament. Existing local tournaments are not silently claimed, uploaded, reassigned, or deleted merely because a user signs in.

## 4. Source of truth and authentication

Room remains the local source of truth during v0.6.3. The upload reads the local Room/domain snapshot and sends a cloud copy. A successful or partial upload must not replace, mutate, delete, or restore local data.

Uploads require a currently authenticated Supabase user. The upload coordinator must check the authenticated session before any cloud write and must send no anonymous cloud writes. Unauthenticated users remain local-only; the UI must explain that sign-in is required for upload.

Logout and session loss do not delete Room data. A session that becomes invalid during upload produces an explicit authentication failure and leaves local data intact.

## 5. Ownership and RLS boundary

The tournament payload must set:

```text
owner_id = auth.uid()
```

The client must not accept an arbitrary owner ID or attempt to claim another account's tournament. Team slots are uploaded with `tournament_id` pointing to the uploaded owned tournament. Players are uploaded with `team_slot_id` pointing to the uploaded owned team slot. Existing v0.6.2 RLS policies remain the authorization boundary; no client-side ownership check substitutes for RLS.

An ownership or RLS rejection is surfaced as a failed upload. The implementation must not retry under another owner, change `owner_id`, detach children, or delete an existing cloud row.

## 6. Local/cloud identity mapping

The mapping is deliberately deterministic and requires no new Room mapping table in v0.6.3:

* A local tournament ID is reused as the Supabase tournament UUID. The upload must reject a local ID that is not a valid UUID instead of silently generating a replacement.
* A local team slot has no standalone Room UUID. Its cloud UUID is `UUID.nameUUIDFromBytes("rank-forge:team-slot:<tournament UUID>:<slot number>".toByteArray(UTF_8))`, derived from the stable local key `(tournament UUID, slot number)`.
* A local roster player is identified by the stable local key `(tournament UUID, slot number, roster position)`. Its cloud UUID is `UUID.nameUUIDFromBytes("rank-forge:player:<tournament UUID>:<slot number>:<roster position>".toByteArray(UTF_8))`. The uploaded `normalized_name` uses the existing `RosterNameNormalizer` behavior; the roster position is an identity input, not a new cloud column.
* The deterministic mapping must be implemented as a pure, unit-testable mapper. Repeating the same upload must produce the same UUIDs. It must not use a new random UUID for an existing local record.

The current local tournament and match models already use UUID-shaped string IDs for generated tournaments and matches, while Room team slots and roster players use composite keys. This mapping handles that existing difference without a destructive Room migration. No future synchronization, remapping, or cloud-to-local identity behavior is implied by this decision.

## 7. Upload trigger and operation shape

The only approved trigger is a manual user action from the selected tournament/roster workflow after local validation. It is not automatic after tournament save, roster save, login, session restoration, or logout.

The action uploads the parent first, then its slots, then its players. The UI exposes loading, success, authentication failure, authorization failure, validation failure, network failure, and partial-upload failure states. Network work remains outside composables and off the main thread.

The v0.6.3 operation is a bounded snapshot upload. It has no background worker, periodic retry, persistent queue, or automatic retry after restart.

## 8. Idempotency boundary

Within v0.6.3, repeated manual upload of the same local records must not create duplicate tournament, slot, or player rows. The implementation must use the stable mapped IDs and the existing approved uniqueness constraints when performing safe insert-or-update requests for the same owner.

This is limited record identity protection for an explicit repeat of the same upload. It does not create an operation log, idempotency key service, retry coordinator, deduplication engine, or cross-device duplicate resolution. General idempotency and duplicate prevention remain deferred to `v0.6.6`.

If a stable ID already belongs to another owner, RLS or a conflict must fail visibly. The client must never overwrite ownership or treat that failure as success.

## 9. Offline and failure behavior

When offline, the user may continue using local Room workflows. The manual cloud action reports that upload is unavailable and leaves the local snapshot unchanged. When unauthenticated, the same local-only behavior applies with an authentication-required message.

There is no persistent offline sync queue, WorkManager job, automatic retry, restart recovery queue, or connectivity monitor in v0.6.3. Those behaviors begin with `v0.6.5` and `v0.6.5.1`.

Because the Data API upload is a sequence of requests rather than a new RPC, a later request may fail after earlier rows were accepted. The implementation must report partial failure, preserve the local source, and allow the user to retry the same explicit action using the same stable IDs. It must not delete accepted cloud rows as an implicit rollback.

## 10. Conflict boundary

No revision comparison, optimistic concurrency workflow, merge decision, conflict record, or conflict UI is included. The existing revision columns are not used to claim v0.6.3 conflict handling.

Revision-based conflict detection is deferred to `v0.6.7`; user-facing conflict resolution is deferred to `v0.6.7.1`. Protected finalization and correction history remain later `v0.6.8` and `v0.6.8.1` work.

## 11. Android architecture boundary

Future implementation may add only the layers needed for this upload flow:

* domain upload input/result contracts and a use case/coordinator that accepts a local snapshot and authenticated-session state;
* a pure mapping/identity component for tournament, slot, and player payloads;
* data-layer cloud DTOs, Supabase data-source/repository code, and mappings between Room/domain data and cloud payloads;
* dependency-injection bindings in the existing data modules;
* narrowly scoped ViewModel state/action handling and Compose rendering for the manual upload action;
* JVM, repository/fake-data-source, and relevant Android UI tests.

The implementation must not put networking, database writes, identity mapping, validation, or scoring in composables. Existing deterministic scoring and validation remain unchanged and independent of UI and cloud transport. No Room schema migration, local deletion, or UI-driven persistence shortcut is approved.

## 12. Supabase boundary

No new migration is approved for v0.6.3. The existing `public.tournaments`, `public.tournament_team_slots`, and `public.players` tables, constraints, indexes, RLS policies, and ownership hierarchy are sufficient for the approved payload. The existing migrations must not be edited.

No new tables, columns, policies, triggers, functions, RPCs, storage buckets, Edge Functions, or production database changes are included. If implementation discovers a concrete schema defect, it must stop and request a separate schema decision rather than silently widening this milestone.

## 13. Required verification

Future implementation must include:

* unit tests for deterministic ID mapping, UUID validation, field/status mapping, normalized names, ownership payload construction, authentication/offline gating, and stable repeated payloads;
* repository or fake-data-source tests for upload ordering, repeat upload behavior, partial failure reporting, authorization failure handling, and preservation of local data;
* RLS/pgTAP changes only if database behavior or database objects change. With the approved no-schema-change scope, existing RLS tests remain the baseline and no new migration test is required;
* `testDebugUnitTest`, `assembleDebug`, and `lintDebug`;
* relevant connected Android tests and manual device verification of authenticated upload, unauthenticated local-only behavior, offline local-only behavior, retry of a partial failure, and repeated upload without duplicate rows when a test backend is available.

Blocked database/device checks must be reported exactly and must not be described as passed.

## 14. Explicit exclusions

The following are outside v0.6.3:

* OCR, screenshots, image storage, and Supabase Storage;
* CSV or Google Sheets export;
* Edge Functions, RPCs, triggers, and new database functions;
* match or match-result synchronization;
* cloud restoration, which is v0.6.3.1;
* persistent offline sync queues and WorkManager synchronization;
* general idempotency/duplicate-prevention infrastructure;
* revision-based conflict detection or resolution;
* protected finalization, corrections, and audit history;
* Android, Room, Gradle, or Supabase refactors unrelated to the approved upload path;
* anonymous cloud writes, shared ownership, collaboration, or upload under another account;
* production Supabase changes.

## 15. Unresolved decisions

No material scope decision is unresolved for this gate. The implementation must preserve the stated deterministic identity mapping and local/cloud status boundary. Any request to add a Room mapping table, cloud restoration, match upload, persistent queue, conflict handling, or a schema change requires a new decision or version-specific approval.

## 16. Verification performed for this decision gate

The requested read-only review was completed on branch `docs/v0.6.3-cloud-upload-decisions`. The roadmap, Phase 6 decisions, relevant canonical architecture/database/Android/backend/testing/security documents, all `docs/ai/` governance files, existing Supabase migrations/tests, and the existing Room/domain/repository/use-case implementation were inspected.

The review confirmed that the working tree was clean before this document was created, the required implementation branch is `feature/v0.6.3-tournament-roster-cloud-upload`, and no source code, tests, migrations, Gradle files, Supabase configuration, or production system was modified.

After creating this document, `git diff --check` completed without errors. `git status --short` showed only this new file. Because the file is intentionally untracked until a later user-approved save, `git diff --name-status` and `git diff --stat` produced no entries for it; no tracked-file diff exists.

The final required Git checks are:

```powershell
git diff --check
git status --short
git diff --name-status
git diff --stat
```
