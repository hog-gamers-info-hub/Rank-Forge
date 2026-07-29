# Phase 7 Closure Audit

## 1. Audit status

Status: Ready to close Phase 7 with documented deferrals.

This audit is documentation-only. It records the repository, merge, implementation, verification, security, persistence, and deferral evidence for Phase 7 - Screenshot Intake and Storage. No Android source code, Gradle files, Room schemas, Supabase migrations, Supabase tests, Supabase config, existing decision documents, or unrelated documentation were changed by this audit.

## 2. Audit scope

Audited canonical Phase 7 versions:

1. v0.7.0 - Photo Picker Integration
2. v0.7.1 - Image Validation
3. v0.7.2 - Match Screenshot Linking
4. v0.7.3 - Duplicate Screenshot Detection
5. v0.7.4 - Local Image Preservation
6. v0.7.5 - Supabase Storage Integration
7. v0.7.6 - Screenshot Metadata

The audit reviewed tracked repository evidence, recent Git history, local and remote branch state, GitHub PR state, Android implementation and tests, Room schema and migration files, Supabase Storage and metadata migrations, and Supabase policy tests.

## 3. Authorities reviewed

Primary authorities:

* `AGENTS.md`
* `docs/project-workflow/00_PHASE_AND_VERSION_ROADMAP.md`
* `docs/project-workflow/19_V0_7_0_PHOTO_PICKER_INTEGRATION_DECISIONS.md`
* `docs/project-workflow/20_V0_7_1_IMAGE_VALIDATION_DECISIONS.md`
* `docs/project-workflow/21_V0_7_2_MATCH_SCREENSHOT_LINKING_DECISIONS.md`
* `docs/project-workflow/22_V0_7_3_DUPLICATE_SCREENSHOT_DETECTION_DECISIONS.md`
* `docs/project-workflow/23_V0_7_4_LOCAL_IMAGE_PRESERVATION_DECISIONS.md`
* `docs/project-workflow/24_V0_7_5_SUPABASE_STORAGE_INTEGRATION_DECISIONS.md`
* `docs/project-workflow/25_V0_7_6_SCREENSHOT_METADATA_DECISIONS.md`

Supporting authorities and evidence:

* `docs/06_ANDROID_APP.md`
* `docs/07_SUPABASE_BACKEND.md`
* `docs/09_TESTING_AND_ACCEPTANCE.md`
* `docs/11_SECURITY_AND_PRIVACY.md`
* `app/src/main/AndroidManifest.xml`
* `app/src/main/java/com/hoggamers/rankforge/presentation/screen/MatchReviewScreen.kt`
* `app/src/main/java/com/hoggamers/rankforge/presentation/screen/MatchReviewViewModel.kt`
* `app/src/main/java/com/hoggamers/rankforge/presentation/screen/MatchReviewUiState.kt`
* `app/src/main/java/com/hoggamers/rankforge/presentation/screen/ImageCandidateValidator.kt`
* `app/src/main/java/com/hoggamers/rankforge/presentation/screen/ScreenshotDuplicateDetector.kt`
* `app/src/main/java/com/hoggamers/rankforge/presentation/screen/LocalImagePreserver.kt`
* `app/src/main/java/com/hoggamers/rankforge/data/cloud/SupabaseScreenshotStorageUploader.kt`
* `app/src/main/java/com/hoggamers/rankforge/data/cloud/ScreenshotMetadataCloudDataSource.kt`
* `app/src/main/java/com/hoggamers/rankforge/data/local/RankForgeDatabase.kt`
* `app/src/main/java/com/hoggamers/rankforge/data/local/TournamentEntities.kt`
* `app/src/main/java/com/hoggamers/rankforge/data/local/TournamentDaos.kt`
* `app/src/main/java/com/hoggamers/rankforge/data/local/ScreenshotMetadataRepository.kt`
* `app/schemas/com.hoggamers.rankforge.data.local.RankForgeDatabase/6.json`
* `supabase/migrations/20260729110000_v0_7_5_match_screenshot_storage.sql`
* `supabase/migrations/20260729120000_v0_7_6_match_screenshot_metadata.sql`
* `supabase/tests/07_v0_7_5_match_screenshot_storage.sql`
* `supabase/tests/08_v0_7_6_match_screenshot_metadata.sql`
* Phase 7 implementation PRs #64, #66, #68, #70, #72, #74, and #76

## 4. Repository and branch state

Required repository commands were run before creating this audit document.

* `git status --short`: clean before audit creation.
* `git rev-list --left-right --count main...origin/main`: `0 0`.
* `git log --oneline --decorate -30`: showed `HEAD`, `main`, `origin/main`, and `origin/HEAD` at merge commit `db8e9ec Merge pull request #76 from hog-gamers-info-hub/feature/v0.7.6-screenshot-metadata`, with Phase 7 PRs #63 through #76 present in recent history.
* `git branch --list`: local branches were `main` and `docs/phase-7-closure-audit`; current branch was `docs/phase-7-closure-audit`.
* `git branch -r`: `origin/main` and completed Phase 7 remote decision/feature branches were present.
* `gh pr list --state open`: no open PRs returned.

Main and `origin/main` were synchronized before this audit document was created. No open Phase 7 implementation PR was found. All Phase 7 implementation branches were merged. Completed Phase 7 remote branches still exist and are recorded as cleanup items in Section 20.

## 5. Canonical Phase 7 version matrix

| Version | Canonical decision | Implementation PR | Merge evidence | Audit result |
| --- | --- | --- | --- | --- |
| v0.7.0 - Photo Picker Integration | `19_V0_7_0_PHOTO_PICKER_INTEGRATION_DECISIONS.md` | #64 `feature/v0.7.0-photo-picker-integration` | Merged 2026-07-28T18:54:10Z | Complete |
| v0.7.1 - Image Validation | `20_V0_7_1_IMAGE_VALIDATION_DECISIONS.md` | #66 `feature/v0.7.1-image-validation` | Merged 2026-07-28T19:17:59Z | Complete |
| v0.7.2 - Match Screenshot Linking | `21_V0_7_2_MATCH_SCREENSHOT_LINKING_DECISIONS.md` | #68 `feature/v0.7.2-match-screenshot-linking` | Merged 2026-07-28T19:46:07Z | Complete |
| v0.7.3 - Duplicate Screenshot Detection | `22_V0_7_3_DUPLICATE_SCREENSHOT_DETECTION_DECISIONS.md` | #70 `feature/v0.7.3-duplicate-screenshot-detection` | Merged 2026-07-28T20:39:59Z | Complete |
| v0.7.4 - Local Image Preservation | `23_V0_7_4_LOCAL_IMAGE_PRESERVATION_DECISIONS.md` | #72 `feature/v0.7.4-local-image-preservation` | Merged 2026-07-29T05:03:46Z | Complete |
| v0.7.5 - Supabase Storage Integration | `24_V0_7_5_SUPABASE_STORAGE_INTEGRATION_DECISIONS.md` | #74 `feature/v0.7.5-supabase-storage-integration` | Merged 2026-07-29T07:27:24Z | Complete |
| v0.7.6 - Screenshot Metadata | `25_V0_7_6_SCREENSHOT_METADATA_DECISIONS.md` | #76 `feature/v0.7.6-screenshot-metadata` | Merged 2026-07-29T08:33:33Z | Complete |

## 6. v0.7.0 audit

Canonical scope: launch the Android system Photo Picker from the existing Match Review workflow, image-only, one image per launch, no new camera/storage/media permissions, temporary selected URI state only.

Implementation evidence:

* `MatchReviewScreen.kt` owns the Compose launcher with `rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia())`.
* `MatchReviewScreen.kt` launches `PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)`.
* `MatchReviewViewModel.kt` coordinates `requestPhotoPicker`, `onPhotoPickerResult`, launch-pending state, active request state, cancellation handling, selected URI state, blank result handling, and controlled launch failure.
* `MatchReviewUiState.kt` includes temporary picker and selected screenshot UI state.
* `app/src/main/AndroidManifest.xml` contains only `INTERNET` and `ACCESS_NETWORK_STATE`; no camera, storage, or media-library permissions are present.

Verification evidence:

* PR #64 reports focused `MatchReviewViewModelTest`, `testDebugUnitTest`, `lintDebug`, `assembleDebug`, `assembleDebugAndroidTest`, `connectedDebugAndroidTest`, and `git diff --check`.
* Focused ViewModel, Compose, navigation, and manifest-permission tests are present in tracked test files.

Audit result: complete.

## 7. v0.7.1 audit

Canonical scope: validate the temporary selected image URI after Photo Picker selection. Accept PNG, JPEG, and WebP candidates when readable and safely decodable by bounds; reject invalid, unreadable, unsupported, oversized, and non-image content with controlled UI feedback. No persistence, OCR, storage, or new permissions.

Implementation evidence:

* `ImageCandidateValidator.kt` validates nonblank URI, image MIME type, supported MIME types, positive dimensions, safe size limit, unreadable URI, and decode failure.
* `AndroidImageCandidateMetadataReader` reads through Android content APIs and decodes bounds with `BitmapFactory.Options.inJustDecodeBounds = true`.
* `MatchReviewViewModel.kt` runs validation after picker result and updates temporary selected/validated/error state.
* `MatchReviewScreen.kt` displays selected and validated feedback and controlled validation errors.

Verification evidence:

* PR #66 reports `testDebugUnitTest`, `lintDebug`, `assembleDebug`, `assembleDebugAndroidTest`, `connectedDebugAndroidTest`, and `git diff --check`.
* `ImageCandidateValidatorTest.kt` covers valid PNG/JPEG/WebP, non-image content, unsupported format, unreadable URI, decode failure, invalid dimensions, oversized candidates, and blank URI.
* `MatchReviewViewModelTest.kt` covers validation result updates, cancellation preservation, invalid selection, and reselection.

Audit result: complete.

## 8. v0.7.2 audit

Canonical scope: link one validated screenshot to the active draft Match Review tournament and match in-session only; support replacement and unlink; protect finalized matches; do not change scoring, standings, finalization, correction audit, Room, Supabase, sync, OCR, storage, hashing, or permissions.

Implementation evidence:

* `MatchReviewViewModel.kt` coordinates `linkScreenshot` and `unlinkScreenshot`, validates match/tournament context, blocks finalized matches, and preserves existing match-result data paths.
* `MatchReviewUiState.kt` represents linked screenshot, link errors, and editable/finalized state.
* `MatchReviewScreen.kt` shows link, replace, linked confirmation, unlink action, and finalized protected feedback.
* Existing scoring/finalization code paths remain separate from screenshot link state.

Verification evidence:

* PR #68 reports `testDebugUnitTest`, `lintDebug`, `assembleDebug`, `assembleDebugAndroidTest`, `connectedDebugAndroidTest`, and `git diff --check`.
* `MatchReviewViewModelTest.kt` covers validated screenshot link, replacement, unlink, missing valid image, tournament/match scoping, finalized protection, and unchanged match data.
* `MatchReviewScreenTest.kt` covers visible linked state, unlink action, and finalized link-action protection.

Audit result: complete.

## 9. v0.7.3 audit

Canonical scope: perform in-session duplicate detection after validation using a SHA-256 fingerprint of source bytes, scoped to the current tournament session. Same-match duplicate is a controlled no-op; cross-match duplicate is rejected. No persistence, cloud, OCR, image mutation, storage permission, or sync changes.

Implementation evidence:

* `ScreenshotDuplicateDetector.kt` uses `MessageDigest.getInstance("SHA-256")` and streams source bytes from `ContentResolver.openInputStream`.
* Duplicate registry state is in-memory and tournament-scoped.
* `MatchReviewViewModel.kt` runs duplicate detection during linking and preserves finalized-match protection before link/preserve/upload transitions.
* `MatchReviewScreen.kt` displays duplicate progress, same-match duplicate info, and cross-match duplicate errors.

Verification evidence:

* PR #70 reports `testDebugUnitTest`, `lintDebug`, `assembleDebug`, `assembleDebugAndroidTest`, `connectedDebugAndroidTest`, manual device smoke verification, and `git diff --check`.
* `ScreenshotDuplicateDetectorTest.kt` covers deterministic same-byte behavior, unreadable source failure, same-match no-op, cross-match rejection, tournament scoping, and unlink registry release.
* `MatchReviewViewModelTest.kt` covers duplicate detection integration and finalized protection.

Audit result: complete.

## 10. v0.7.4 audit

Canonical scope: after validation, duplicate detection, and linking, preserve the original selected image bytes into app-private internal storage using deterministic tournament/match-scoped paths, MIME-derived extension, atomic write, safe replacement cleanup, and draft unlink cleanup. No Room metadata, Supabase, sync, OCR, image mutation, export, or new permissions in this version.

Implementation evidence:

* `LocalImagePreserver.kt` uses `context.filesDir`, `ContentResolver.openInputStream`, deterministic `screenshots/<tournament>/<match>/original.<extension>` paths, MIME-derived extensions, temporary files, `Files.move` with `StandardCopyOption.ATOMIC_MOVE` and `REPLACE_EXISTING`, and safe cleanup under the app-private screenshot root.
* `MatchReviewViewModel.kt` runs preservation after a successful valid, non-duplicate draft link, preserves prior state on failure, blocks finalized matches, and coordinates unlink cleanup.
* `MatchReviewScreen.kt` displays preservation progress, success, cleanup failure, and protected-match feedback.

Verification evidence:

* PR #72 reports `testDebugUnitTest`, `lintDebug`, `assembleDebug`, `assembleDebugAndroidTest`, `connectedDebugAndroidTest`, manual device smoke verification, and `git diff --check`.
* `LocalImagePreserverTest.kt` covers byte-for-byte copy, deterministic tournament/match scoped file, MIME-derived extension, replacement cleanup, atomic move failure, source read failure, and cleanup failure.
* `MatchReviewViewModelTest.kt` covers local preservation, replacement, unlink, failure handling, and finalized protection.

Audit result: complete.

## 11. v0.7.5 audit

Canonical scope: upload the app-private preserved screenshot file to private Supabase Storage bucket `match-screenshots` using object path `users/<auth-user-id>/tournaments/<tournament-id>/matches/<match-id>/original.<extension>`, byte-for-byte, with deterministic upsert replacement. No Room metadata, persistent sync queue, OCR, public URL, image mutation, cloud delete, or new Android permissions in this version.

Implementation evidence:

* `SupabaseScreenshotStorageUploader.kt` uploads the app-private local `File`, requires an authenticated Supabase session, validates tournament and match IDs, derives object path using authenticated user ID, and uploads to `match-screenshots` with upsert and content type.
* `SupabaseClientProvider.kt` installs Auth, PostgREST, and Storage plugins.
* `CloudUploadDataModule.kt` binds `SupabaseScreenshotStorageUploader`.
* `MatchReviewViewModel.kt` starts upload only after local preservation succeeds, reports upload status in presentation state, preserves local file state on upload failure, and supports retry.
* `supabase/migrations/20260729110000_v0_7_5_match_screenshot_storage.sql` creates a private bucket, restricts MIME types to PNG/JPEG/WebP, and defines owner-scoped insert, select, and update policies for deterministic upsert behavior.
* `supabase/tests/07_v0_7_5_match_screenshot_storage.sql` checks private bucket, approved MIME types, authenticated-only policies, owner checks, path checks, insert/select/update coverage, and absence of delete policy.

Verification evidence:

* PR #74 reports `testDebugUnitTest`, `lintDebug`, `assembleDebug`, `assembleDebugAndroidTest`, `connectedDebugAndroidTest passed: 144 tests`, local Supabase database reset, focused Supabase Storage policy test, full Supabase database test suite, manual device and Storage verification, and `git diff --check`.
* `SupabaseScreenshotStorageUploaderTest.kt` covers object path and upload boundary behavior.
* `MatchReviewViewModelTest.kt` covers upload state, controlled failures, retry, and preserving local state.

Audit result: complete.

## 12. v0.7.6 audit

Canonical scope: persist one screenshot metadata record per match in Room and private Supabase metadata, including owner, tournament, match, local relative path, image properties, SHA-256, Storage references, local/upload status, timestamps, and revision. Preserve finalized-match protection. Do not add OCR, score extraction, image mutation, public sharing, persistent screenshot sync queue changes, automatic orphan scanning, automatic cloud-object cleanup, cross-device restoration workflow, or new Android permissions.

Implementation evidence:

* `RankForgeDatabase.kt` is version `6`, includes `ScreenshotMetadataEntity`, exposes `screenshotMetadataDao`, and defines `MIGRATION_5_6`.
* `TournamentEntities.kt` defines `ScreenshotMetadataEntity` with primary key `matchId`, tournament and owner IDs, relative path, file extension, MIME type, dimensions, byte size, SHA-256, Storage references, statuses, timestamps, and revision.
* `TournamentDaos.kt` defines focused metadata DAO operations for observe/get/list/upsert/upload state/local missing/cleanup failure/delete.
* `ScreenshotMetadataRepository.kt` wraps the DAO with focused repository operations.
* Room schema `app/schemas/com.hoggamers.rankforge.data.local.RankForgeDatabase/6.json` contains the exported `screenshot_metadata` table and indexes.
* `ScreenshotMetadataCloudDataSource.kt` upserts and deletes private cloud metadata through authenticated PostgREST client behavior.
* `supabase/migrations/20260729120000_v0_7_6_match_screenshot_metadata.sql` creates `public.match_screenshot_metadata`, constraints, indexes, RLS, and authenticated owner-scoped select/insert/update/delete policies with update `USING` and `WITH CHECK`.
* `supabase/tests/08_v0_7_6_match_screenshot_metadata.sql` verifies metadata table shape, primary and foreign keys, required indexes, RLS, authenticated-only policies, owner checks, and representative constraints.
* `MatchReviewViewModel.kt` restores metadata state, marks missing local files, writes metadata after preservation, updates upload status, handles cloud metadata failures, increments revisions, and blocks finalized-match mutation.

Verification evidence:

* PR #76 reports `testDebugUnitTest`, `lintDebug`, `assembleDebug`, `assembleDebugAndroidTest`, `connectedDebugAndroidTest`, Room migration and DAO tests, local Supabase database reset, full Supabase database test suite, manual app-restart restoration, manual replacement/unlink/missing-file/upload/finalized-match verification, and `git diff --check`.
* `RankForgeDatabaseMigrationTest.kt` covers migration 5 to 6 and preservation of existing match data.
* `ScreenshotMetadataDaoTest.kt` covers DAO metadata behavior.
* `MatchReviewViewModelTest.kt` covers metadata creation, upload-state update, cloud metadata failure, Room write failure, restoration, missing-file handling, replacement, unlink cleanup, and finalized protection.
* `MatchReviewScreenTest.kt` covers restored, uploaded, missing, upload-error, and finalized UI states.

Audit result: complete.

## 13. Automated verification evidence

Repository and PR evidence shows the following checks were reported for the implementation PRs:

* v0.7.0 PR #64: focused `MatchReviewViewModelTest`, `testDebugUnitTest`, `lintDebug`, `assembleDebug`, `assembleDebugAndroidTest`, `connectedDebugAndroidTest`, and `git diff --check`.
* v0.7.1 PR #66: `testDebugUnitTest`, `lintDebug`, `assembleDebug`, `assembleDebugAndroidTest`, `connectedDebugAndroidTest`, and `git diff --check`.
* v0.7.2 PR #68: `testDebugUnitTest`, `lintDebug`, `assembleDebug`, `assembleDebugAndroidTest`, `connectedDebugAndroidTest`, and `git diff --check`.
* v0.7.3 PR #70: `testDebugUnitTest`, `lintDebug`, `assembleDebug`, `assembleDebugAndroidTest`, `connectedDebugAndroidTest`, and `git diff --check`.
* v0.7.4 PR #72: `testDebugUnitTest`, `lintDebug`, `assembleDebug`, `assembleDebugAndroidTest`, `connectedDebugAndroidTest`, and `git diff --check`.
* v0.7.5 PR #74: `testDebugUnitTest`, `lintDebug`, `assembleDebug`, `assembleDebugAndroidTest`, `connectedDebugAndroidTest passed: 144 tests`, local Supabase database reset, focused Supabase Storage policy test, full Supabase database test suite, and `git diff --check`.
* v0.7.6 PR #76: `testDebugUnitTest`, `lintDebug`, `assembleDebug`, `assembleDebugAndroidTest`, `connectedDebugAndroidTest`, Room migration and DAO tests, local Supabase database reset, full Supabase database test suite, and `git diff --check`.

Tracked focused test evidence includes:

* `ImageCandidateValidatorTest.kt`
* `ScreenshotDuplicateDetectorTest.kt`
* `LocalImagePreserverTest.kt`
* `SupabaseScreenshotStorageUploaderTest.kt`
* `MatchReviewViewModelTest.kt`
* `MatchReviewScreenTest.kt`
* `RankForgeNavigationTest.kt`
* `ManifestPermissionTest.kt`
* `RankForgeDatabaseMigrationTest.kt`
* `ScreenshotMetadataDaoTest.kt`
* `supabase/tests/07_v0_7_5_match_screenshot_storage.sql`
* `supabase/tests/08_v0_7_6_match_screenshot_metadata.sql`

This audit did not rerun the full Android or Supabase suites because repository and PR evidence was present. Audit-only verification commands run after creating this document are recorded outside this section by the final audit report.

## 14. Connected-device and manual verification evidence

Connected-device evidence:

* PRs #64, #66, #68, #70, #72, and #76 report `connectedDebugAndroidTest`.
* PR #74 reports `connectedDebugAndroidTest passed: 144 tests`.

Manual or connected workflow evidence:

* PR #70 reports manual device smoke verification for duplicate screenshot detection.
* PR #72 reports manual device smoke verification for local image preservation.
* PR #74 reports manual device and Storage verification for Supabase Storage upload.
* PR #76 reports manual app-restart restoration and manual replacement, unlink, missing-file, upload, and finalized-match verification.

No new manual verification instructions are introduced by this audit.

## 15. Room migration and persistence audit

Room changes are limited to the v0.7.6 metadata boundary:

* Database version is `6`.
* `MIGRATION_5_6` adds `screenshot_metadata`.
* Exported schema `6.json` includes `screenshot_metadata`.
* `screenshot_metadata` is keyed by `match_id` and indexed by tournament ID, owner user ID, SHA-256, and upload status.
* Existing match/tournament persistence tables remain present.
* v0.7.0 through v0.7.5 did not introduce Room screenshot metadata; durable metadata begins at v0.7.6 as approved.

No extra Room migration beyond 5 to 6 was found for Phase 7 screenshot metadata.

## 16. Supabase Storage, metadata, and RLS audit

Supabase Storage audit:

* `20260729110000_v0_7_5_match_screenshot_storage.sql` creates the private `match-screenshots` bucket.
* Allowed MIME types are limited to `image/png`, `image/jpeg`, and `image/webp`.
* Storage object policies are scoped to `authenticated` users.
* Insert, select, and update policies are present for deterministic replacement/upsert.
* No Storage delete policy is present, which matches the v0.7.5 and v0.7.6 deferral of cloud object deletion.
* Policies validate user, tournament, match, bucket, and deterministic filename path segments.

Supabase metadata audit:

* `20260729120000_v0_7_6_match_screenshot_metadata.sql` creates `public.match_screenshot_metadata`.
* RLS is enabled.
* Policies target `authenticated` users only.
* Select, insert, update, and delete policies enforce owner and tournament/match membership.
* The update policy includes both `USING` and `WITH CHECK`.
* No `anon` policy, public access model, public URL model, `auth.role()` dependency, user-editable metadata claim, or `SECURITY DEFINER` helper was found in the Phase 7 screenshot metadata migration.
* Supabase tests `07_v0_7_5_match_screenshot_storage.sql` and `08_v0_7_6_match_screenshot_metadata.sql` cover the core privacy, ownership, action, and constraint model.

Audit result: Supabase Storage, metadata, and RLS evidence is sufficient for Phase 7 closure.

## 17. Scope-preservation audit

Scope preservation evidence:

* Photo Picker remains in the existing Match Review workflow; no separate screenshot-management screen was found.
* Android manifest permissions remain limited to network permissions already needed by backend behavior: `INTERNET` and `ACCESS_NETWORK_STATE`.
* No camera, storage, media-library, or broad filesystem permissions were added.
* The screenshot workflow is represented in Match Review UI and ViewModel state.
* Duplicate detection uses source-byte SHA-256 and does not persist the duplicate registry.
* Local preservation uses app-private storage and deterministic match-scoped paths.
* Supabase upload uses the preserved file, not the original picker URI.
* v0.7.6 persists metadata only and does not add OCR results or extracted scores.
* Existing persistent sync queue operation identity remains tournament-based with no screenshot operation type.
* Scoring, standings, finalization, and protected correction behavior remain separate from screenshot intake/storage code.

No out-of-scope OCR, score extraction, multiple screenshots per match, public sharing, export behavior, camera capture, or Android permission changes were found in the audited implementation surfaces.

## 18. Security and privacy audit

Security and privacy evidence:

* Android Photo Picker is used for least-access image selection.
* No storage, media, camera, or broad filesystem permission was added.
* App-private local preservation uses deterministic IDs and does not use raw content URI strings as durable file names.
* Supabase Storage bucket is private.
* Storage policies are owner-scoped and authenticated-only.
* Metadata table has RLS enabled and owner-scoped policies.
* Supabase policies avoid public access and anonymous policies.
* Android upload code uses the authenticated Supabase client session and does not introduce service-role credentials.
* Metadata decisions and implementation avoid storing Photo Picker content URIs, absolute local paths, public URLs, OCR content, extracted scores, raw exception text, or screenshot bytes in metadata.

Audit result: Phase 7 security and privacy posture is consistent with the approved closure boundary.

## 19. Documented deferrals

The following are documented future work and are not blockers for Phase 7 closure:

* OCR and score extraction remain Phase 8 work.
* Persistent screenshot upload/metadata queue integration remains deferred because the current queue identity is tournament-based and cannot safely identify one screenshot operation per match.
* Automatic orphan-file discovery and reconciliation remain deferred.
* Supabase Storage object deletion during unlink remains deferred.
* Cross-device screenshot restoration workflow remains deferred.
* Multiple screenshots per match remain out of scope.
* Public screenshot sharing and exports remain out of scope.

## 20. Remaining risks or cleanup items

Cleanup items:

* Completed Phase 7 remote branches still exist under `origin/docs/v0.7.0-photo-picker-decisions`, `origin/feature/v0.7.0-photo-picker-integration`, `origin/docs/v0.7.1-image-validation-decisions`, `origin/feature/v0.7.1-image-validation`, `origin/docs/v0.7.2-match-screenshot-linking-decisions`, `origin/feature/v0.7.2-match-screenshot-linking`, `origin/docs/v0.7.3-duplicate-screenshot-detection-decisions`, `origin/feature/v0.7.3-duplicate-screenshot-detection`, `origin/docs/v0.7.4-local-image-preservation-decisions`, `origin/feature/v0.7.4-local-image-preservation`, `origin/docs/v0.7.5-supabase-storage-integration-decisions`, `origin/feature/v0.7.5-supabase-storage-integration`, `origin/docs/v0.7.6-screenshot-metadata-decisions`, and `origin/feature/v0.7.6-screenshot-metadata`.
* The current closure-audit branch must be reviewed, merged, and deleted before Phase 8 branch work starts.

Known limitations carried forward as deferrals:

* Screenshot upload and metadata failures are not replayed through the persistent sync queue after app restart.
* Supabase Storage objects are not deleted during unlink.
* Orphan local files and orphan Storage objects are not automatically discovered or reconciled.
* Cross-device restoration of screenshot image files is not implemented.

No implementation blocker was found that prevents Phase 7 closure with the deferrals recorded in Section 19.

## 21. Closure decision

Decision: Ready to close Phase 7 with documented deferrals.

Rationale:

* All seven canonical Phase 7 decision documents exist.
* All seven Phase 7 implementation PRs are merged.
* `main` and `origin/main` are synchronized at `db8e9ec`.
* No open PRs were returned by `gh pr list --state open`.
* Implementation evidence exists for Photo Picker, image validation, match screenshot linking, duplicate detection, local image preservation, Supabase Storage upload, and screenshot metadata persistence.
* Automated, connected-device, Supabase, and manual workflow verification evidence is recorded in the merged implementation PRs.
* Security, privacy, Room, Supabase Storage, metadata, and RLS boundaries match the approved Phase 7 scope.
* Remaining items are cleanup or explicitly documented deferrals, not Phase 7 implementation blockers.

## 22. Phase 8 entry conditions

Phase 8 may begin only after all of the following are true:

* This closure audit has been reviewed and merged.
* `main` is clean and synchronized.
* The closure-audit branch is deleted.
* Phase 8 canonical versions and decisions are reviewed.
* Explicit user approval is given to begin Phase 8.
