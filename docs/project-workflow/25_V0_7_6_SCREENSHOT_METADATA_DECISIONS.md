# v0.7.6 Screenshot Metadata Decisions

## 1. Status

**Approved for implementation after decision-document review and merge**

Expected implementation branch: `feature/v0.7.6-screenshot-metadata`

This document is the canonical implementation decision for Phase 7 v0.7.6. It authorizes screenshot metadata persistence only; it does not authorize Android implementation, OCR, image processing, exports, or multiple screenshots per match.

## 2. Objective

Persist one canonical screenshot metadata record for each match so the app can restore the relationship between:

* authenticated owner;
* tournament;
* match;
* app-private preserved screenshot file;
* image properties and SHA-256 fingerprint;
* private Supabase Storage object;
* local preservation state; and
* cloud upload state.

v0.7.6 persists metadata only. It does not introduce OCR, score extraction, image processing, public sharing, exports, or multiple screenshots per match.

## 3. Canonical scope

v0.7.6 builds on the completed v0.7.0–v0.7.5 screenshot workflow. Metadata is created only after the existing validation, duplicate detection, draft-match linking, local preservation, and applicable Storage upload behavior have produced a valid state.

The existing Match Review workflow remains the entry point. Metadata is durable local Room state and a private Supabase metadata record, but it is not a new screenshot-management screen. Tournament, roster, match-processing, scoring, standings, authentication, correction audit, existing persistence, and existing synchronization behavior remain unchanged except for the narrowly scoped screenshot metadata state required here.

## 4. Identity and cardinality

* One screenshot metadata record is allowed per match.
* `matchId` is the canonical metadata identity and primary key.
* Do not introduce a separate screenshot metadata UUID.
* Match IDs are already globally unique and provide deterministic local and cloud identity.
* `tournamentId` and `ownerUserId` are stored for ownership, querying, validation, and RLS.
* Draft screenshot replacement updates the existing metadata record for that match.
* Finalized matches remain protected from metadata mutation.
* Multiple screenshots per match remain out of scope.

## 5. Local Room metadata model

Introduce a Room entity equivalent to `ScreenshotMetadataEntity` with table name `screenshot_metadata`.

Use the existing local conventions: UUID-like identifiers are stored as `String`, Room timestamps are UTC epoch-millisecond `Long` values, enum states are stored by their stable name, image dimensions are `Int`, and byte sizes and revisions are `Long`.

| Field | Room representation and rules |
| --- | --- |
| `matchId` | `String`, primary key; canonical match identity and required foreign key to `matches.id` |
| `tournamentId` | Required `String`, indexed; must match the referenced match tournament |
| `ownerUserId` | Required `String`; authenticated owner identity |
| `localRelativePath` | Required `String`; path relative to the app-private screenshot root only |
| `fileExtension` | Required `String`; accepted MIME-derived extension (`png`, `jpg`, or `webp`) |
| `mimeType` | Required `String`; accepted image MIME type |
| `width` | Required positive `Int` |
| `height` | Required positive `Int` |
| `byteSize` | Required positive `Long` |
| `sha256` | Required lowercase hexadecimal SHA-256 value, exactly 64 characters |
| `storageBucket` | Nullable `String` until cloud upload succeeds; then `match-screenshots` |
| `storageObjectPath` | Nullable `String` until cloud upload succeeds; then the deterministic v0.7.5 object path |
| `localStatus` | Required stable status name: `PRESERVED`, `MISSING`, or `CLEANUP_FAILED` |
| `uploadStatus` | Required stable status name: `PENDING`, `UPLOADED`, or `FAILED` |
| `uploadFailureCode` | Nullable controlled code; never raw exception text |
| `createdAt` | Required UTC epoch-millisecond `Long`, set on first metadata creation |
| `updatedAt` | Required UTC epoch-millisecond `Long`, changed on every metadata mutation |
| `preservedAt` | Required UTC epoch-millisecond `Long`, set after successful local preservation |
| `uploadedAt` | Nullable UTC epoch-millisecond `Long`, set only after successful Storage upload |
| `revision` | Required positive `Long`, starting at `1` |

Do not store the original Photo Picker content URI, an absolute device-specific file path, OCR data, extracted placement or kill values, public URLs, raw exception messages, or image-processing output.

## 6. Room relationships, DAO, and migration

### Relationships and indexes

* `matchId` references the existing `matches` table and its exact current `id` column with `ON DELETE CASCADE`.
* Match deletion cascades to screenshot metadata.
* Tournament deletion removes screenshot metadata through the existing tournament → match → metadata relationship.
* Add indexes on `tournamentId`, `ownerUserId`, `sha256`, and `uploadStatus`.
* The primary key on `matchId` enforces one metadata record per match.
* `localRelativePath` is always relative to the app-private screenshot root.
* Never persist an absolute path or an external content URI.

### DAO contract

Add only focused operations to a screenshot metadata DAO:

* observe metadata by match ID;
* get metadata by match ID;
* list metadata by tournament ID;
* insert or replace metadata for a draft match;
* update the upload result;
* update local-file-missing status;
* delete metadata by match ID; and
* delete metadata by tournament ID.

DAO writes are `suspend` functions. Observed metadata uses the project’s existing `Flow` convention. Do not add broad screenshot queries unrelated to v0.7.6.

### Room migration

The current `RankForgeDatabase` version is `5`; the next implementation migration is exactly `5` to `6`.

The migration must:

* increase the database version by exactly one;
* add only the `screenshot_metadata` table and the required indexes;
* preserve all existing data;
* leave tournament, roster, match, scoring, standings, authentication, and sync tables unchanged;
* export the new Room schema; and
* include a migration test proving existing data survives.

Do not invent another database version or modify an already-applied migration. Add DAO tests for insertion, replacement, observation, upload-state update, and deletion.

## 7. Supabase metadata model

Add a dedicated private table named `public.match_screenshot_metadata` with these canonical columns:

```text
match_id uuid primary key
owner_id uuid not null
tournament_id uuid not null
local_file_extension text not null
mime_type text not null
width integer not null
height integer not null
byte_size bigint not null
sha256 text not null
storage_bucket text
storage_object_path text
local_status text not null
upload_status text not null
upload_failure_code text
preserved_at timestamptz not null
uploaded_at timestamptz
revision bigint not null default 1
created_at timestamptz not null default now()
updated_at timestamptz not null default now()
```

`match_id` references the existing cloud match table, `public.matches(id)`, and `tournament_id` references `public.tournaments(id)`. `local_file_extension` describes the preserved image format; `localRelativePath` is device-local and is therefore not stored in Supabase.

Do not store external Photo Picker URIs, absolute local paths, public URLs, OCR data, extracted score values, or image-processing output.

## 8. Supabase constraints, indexes, and RLS

### Constraints and indexes

The implementation migration must enforce:

* `owner_id` remains consistent with the owner of the referenced tournament and match;
* `width > 0` and `height > 0`;
* `byte_size > 0`;
* `sha256` contains exactly 64 lowercase hexadecimal characters;
* `storage_bucket`, when present, equals `match-screenshots`;
* `storage_object_path`, when present, matches the authenticated-owner/tournament/match path contract from v0.7.5; and
* `upload_status = 'UPLOADED'` requires non-null `storage_bucket`, `storage_object_path`, and `uploaded_at`.

Add indexes on `owner_id`, `tournament_id`, `sha256`, `upload_status`, and `updated_at`. Use stable checks for `local_status` (`PRESERVED`, `MISSING`, `CLEANUP_FAILED`) and `upload_status` (`PENDING`, `UPLOADED`, `FAILED`).

### RLS

Enable RLS on `public.match_screenshot_metadata`.

Policies apply only to `authenticated` and provide owner-scoped `SELECT`, `INSERT`, `UPDATE`, and `DELETE`. Every policy compares `owner_id` with `(select auth.uid())`. Insert and update policies also verify that the referenced tournament and match belong to the authenticated owner under the existing ownership model. Update policies require both `USING` and `WITH CHECK` clauses.

Cross-account reads and writes must be denied. Do not use `auth.role()`, user-editable metadata claims, or `SECURITY DEFINER`. Do not allow anonymous or public access. The v0.7.5 `match-screenshots` bucket and object policies remain private and unchanged except for any narrowly required metadata integration.

## 9. Status model

### Local status

Persist only:

* `PRESERVED` — metadata was created after successful local preservation and the expected file is present;
* `MISSING` — the expected app-private file is no longer present; and
* `CLEANUP_FAILED` — draft unlink or replacement cleanup failed and the metadata was retained.

Do not persist temporary `PRESERVING` or other in-progress states. Temporary work remains in presentation state.

### Upload status

Persist only:

* `PENDING` — local metadata exists and cloud metadata/object completion is not confirmed;
* `UPLOADED` — the private Storage object and metadata upload succeeded; and
* `FAILED` — the cloud operation failed and a controlled `uploadFailureCode` is stored.

New preserved metadata starts at `PENDING`. Successful Storage upload changes it to `UPLOADED`; failure changes it to `FAILED`. Replacement resets it to `PENDING`. Do not persist `UPLOADING`; it remains presentation-only. Do not add OCR or Phase 8 processing statuses.

## 10. Timestamp and revision rules

* All timestamps represent UTC.
* Room uses the existing UTC epoch-millisecond `Long` representation used by local persistence.
* Do not introduce a second local timestamp serialization system.
* `createdAt` is set when metadata is first created.
* `updatedAt` changes on every metadata mutation.
* `preservedAt` records successful local preservation.
* `uploadedAt` is set only after successful Storage upload.
* Offline-created timestamps use the device UTC clock.
* Supabase `created_at`, `updated_at`, `preserved_at`, and `uploaded_at` use `timestamptz`.
* Cloud `updated_at` is server-maintained.
* Successful cloud metadata writes reconcile local `updatedAt`, `uploadedAt`, and `revision`.
* `revision` starts at `1` and increases for replacement and every persisted metadata mutation requiring a new canonical state.

## 11. Initial preservation sequence

1. Validate the screenshot.
2. Confirm it is not a prohibited duplicate.
3. Link it to the draft match.
4. Preserve it atomically in app-private storage.
5. Calculate dimensions, byte size, MIME type, extension, and SHA-256.
6. Write Room metadata with `localStatus = PRESERVED`, `uploadStatus = PENDING`, and `revision = 1`.
7. Attempt private Storage upload when authenticated and online.
8. After successful upload, update local metadata to `uploadStatus = UPLOADED`, populate the bucket and object path, set `uploadedAt`, and increment `revision`.
9. Upsert the Supabase metadata row.
10. If the cloud metadata write fails, keep the local preserved file and local metadata, mark the controlled failure state, and do not corrupt match data.

Metadata writes must not alter placement, kills, totals, scoring, standings, finalization, correction audit behavior, or existing synchronization records.

## 12. Draft replacement sequence

1. Preserve the replacement into a temporary app-private file.
2. Validate all replacement metadata.
3. Atomically move the replacement into the deterministic match location.
4. Update the Room metadata record in one transaction.
5. Increment `revision`.
6. Reset `uploadStatus` to `PENDING`, clear `uploadedAt`, and clear stale cloud references until replacement upload succeeds.
7. Upload using the deterministic v0.7.5 Storage object path with upsert.
8. Update local and cloud metadata after successful upload.
9. Do not remove the previous safe local state or metadata until replacement succeeds.

Replacement is allowed only for a draft match and continues to enforce finalized-match protection, ownership, metadata validation, and one-record-per-match identity.

## 13. Finalized-match protection

Finalized matches reject preservation, replacement, metadata mutation, and unlink. The implementation must provide controlled UI feedback and must not modify scoring, standings, finalization, correction audit data, local metadata, or cloud metadata for a protected match.

Duplicate detection, local preservation, Storage upload, and metadata operations must not bypass this protection.

## 14. Unlink behavior

Unlink is allowed only for draft matches.

1. Delete the app-private file first.
2. Delete local Room metadata only after file deletion succeeds.
3. If deletion fails, retain metadata, set `localStatus = CLEANUP_FAILED`, and show controlled UI feedback.
4. When authenticated and online, delete the Supabase metadata row under owner-scoped RLS.
5. Do not delete the Supabase Storage object in v0.7.6.

A private orphaned Storage object may remain at the deterministic path until a later cleanup version. Cloud object deletion is intentionally deferred; unlink must not silently delete cloud objects. Failed metadata deletion or cleanup must not crash or discard the last known-good local state.

## 15. Existing-file and backfill policy

* Do not automatically scan app-private storage.
* Do not infer match ownership from filenames during migration.
* Do not create metadata for unknown pre-v0.7.6 files.
* Existing untracked files remain untouched.
* Metadata is created when a screenshot is newly preserved or replaced after v0.7.6.
* Automatic orphan-file discovery and reconciliation are deferred.

This policy avoids incorrect match associations and destructive cleanup.

## 16. Sync boundary and known limitation

Do not extend the persistent sync queue in v0.7.6. The current queue identity is operation type plus tournament ID and cannot safely identify one screenshot metadata operation per match.

Metadata cloud writes use immediate authenticated online upsert. Failed cloud metadata writes remain represented locally by `uploadStatus = FAILED` and a controlled failure code. Automatic background retry after app restart is deferred until queue identity includes match/screenshot metadata identity. Do not reuse the tournament-only queue identity.

The lack of background replay is a documented consistency limitation, not an implementation omission.

## 17. Error handling

The implementation must produce controlled UI and persisted controlled codes for:

* Room metadata insert or update failure;
* Room migration failure;
* missing local file;
* invalid relative path;
* fingerprint mismatch;
* unsupported MIME type;
* local/cloud metadata disagreement;
* Supabase metadata write failure;
* RLS or ownership failure;
* missing Storage object path;
* replacement failure;
* unlink cleanup failure; and
* finalized-match mutation attempts.

Persist only stable codes such as `ROOM_WRITE_FAILED`, `MIGRATION_FAILED`, `LOCAL_FILE_MISSING`, `INVALID_RELATIVE_PATH`, `FINGERPRINT_MISMATCH`, `UNSUPPORTED_MIME_TYPE`, `CLOUD_METADATA_WRITE_FAILED`, `RLS_DENIED`, `STORAGE_OBJECT_MISSING`, `REPLACEMENT_FAILED`, `CLEANUP_FAILED`, and `FINALIZED_MATCH_PROTECTED`. Do not persist raw exception messages, private paths, URIs, credentials, or screenshot content.

Failures must not crash the app, corrupt match data, or delete the last known-good local file or metadata during a failed replacement.

## 18. Explicit exclusions

v0.7.6 does not implement:

* OCR;
* score, placement, or kill extraction;
* image processing;
* cropping, resizing, rotation, compression, or enhancement;
* multiple screenshots per match;
* public URLs or public sharing;
* exports;
* persistent screenshot sync queue changes;
* automatic orphan-file scanning;
* automatic cloud-object cleanup;
* a cross-device restoration workflow;
* Android storage, media, or camera permissions; or
* unrelated tournament or match schema redesign.

## 19. Automated acceptance criteria

The future implementation is acceptable only when automated coverage verifies:

* Room migration 5→6 creates only the approved table and indexes.
* Existing tournament, roster, match, scoring, standings, authentication, and sync data survives migration.
* DAO insert, observe, get, replacement, upload-state update, local-file-missing update, and deletion behavior works.
* The primary key enforces one metadata record per match.
* Draft replacement performs an upsert and increments `revision`.
* Metadata restores after app restart.
* Missing local files become `MISSING` without a crash.
* Finalized matches reject metadata preservation, replacement, mutation, and unlink.
* Supabase schema constraints reject invalid dimensions, byte size, SHA-256, statuses, bucket, object path, and uploaded-state combinations.
* Supabase owner-scoped RLS allows only the authenticated owner and denies cross-account reads and writes.
* Metadata upload-state transitions are persisted correctly for pending, uploaded, and failed operations.
* Replacement preserves the prior safe state until the new state succeeds.
* Unlink cleanup behavior and `CLEANUP_FAILED` state are correct.
* Match Review ViewModel and Compose UI expose restoration, replacement, unlink, protected-match, and controlled-error states.
* No new Android storage, media, or camera permissions are added.
* Existing v0.7.0 Photo Picker, v0.7.1 validation, v0.7.2 linking, v0.7.3 duplicate detection, v0.7.4 local preservation, and v0.7.5 Storage behavior remains valid.

## 20. Required implementation verification

The future implementation must run:

* `testDebugUnitTest`;
* `lintDebug`;
* `assembleDebug`;
* `assembleDebugAndroidTest`;
* `connectedDebugAndroidTest`;
* local Supabase migration reset;
* focused Supabase metadata schema and RLS tests;
* the full Supabase database test suite; and
* `git diff --check`.

Required automated coverage includes Room migration and DAO tests, Match Review ViewModel and Compose UI tests, metadata upload-state tests, owner-scoped RLS tests, cross-account denial tests, finalized protection tests, replacement revision tests, unlink cleanup tests, and the manifest permission test. No manual verification instructions are part of this decision gate.

## 21. Expected implementation branch

`feature/v0.7.6-screenshot-metadata`

## 22. Completion conditions

v0.7.6 is complete only when one durable Room and private Supabase metadata record can restore each approved match screenshot relationship, local and cloud status transitions are controlled and owner-scoped, draft replacement and unlink follow the approved safe-state rules, finalized matches remain protected, migration and schema exports are verified, and all required automated checks pass.

The implementation must remain limited to screenshot metadata persistence and reconciliation. It must not introduce OCR, image processing, exports, multiple screenshots, public access, automatic orphan scanning, cloud-object cleanup, persistent screenshot queue changes, new Android permissions, or unrelated tournament and match schema redesign.
