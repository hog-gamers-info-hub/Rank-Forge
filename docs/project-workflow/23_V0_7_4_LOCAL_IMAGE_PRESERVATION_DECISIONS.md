# V0.7.4 Local Image Preservation Decisions

## 1. Status

Approved implementation decision gate for Phase 7 v0.7.4. This document defines how a validated, non-duplicate, in-session linked screenshot is copied into app-private storage before implementation begins.

## 2. Objective

Preserve the original selected screenshot bytes as an app-owned local file after v0.7.0 Photo Picker selection, v0.7.1 validation, v0.7.2 match linking, and v0.7.3 duplicate detection succeed. v0.7.4 provides local image ownership only; it does not add cloud storage, OCR, image processing, or durable screenshot metadata.

## 3. Canonical Scope

Preservation runs from the existing Match Review workflow after a candidate is validated, accepted as non-duplicate, and linked to the active tournament and match. It uses the current `(tournamentId, matchId)` context and does not create a separate screenshot-management screen.

The preserved file is app-private and copied from the selected content URI. The selected URI, link state, preservation status, and any cleanup state remain presentation or application state for the current session unless a later approved version defines durable metadata.

## 4. Approved Decisions

### Preservation entry point

* Start preservation only after successful v0.7.1 validation, v0.7.3 duplicate evaluation, and v0.7.2 linking eligibility.
* Keep preservation coordination in the existing Match Review workflow.
* Require the active tournament ID and match ID before creating or replacing a local file.
* Do not create a separate screenshot-management screen.
* Existing Photo Picker, validation, duplicate-detection, and match-linking rules remain authoritative.

### Local file ownership

* Copy the original selected image bytes into `Context.filesDir` or an equivalent app-private internal-storage directory.
* Do not request storage, media-library, camera, or broad filesystem permissions.
* Preserve bytes by streaming from the readable content URI to the app-private file.
* Do not decode and re-encode the image for preservation.
* Do not crop, resize, compress, rotate, enhance, mutate, or otherwise alter the content.
* Do not run OCR or score extraction.
* Do not upload the file or expose it through public or external storage.

### Deterministic path and naming

The approved baseline path is:

`<app-private-files>/screenshots/<tournament-segment>/<match-segment>/original.<extension>`

Path rules:

* The screenshots root is created below app-private internal storage.
* Tournament and match segments are derived only from their validated IDs using a deterministic, collision-free, filesystem-safe encoding.
* Raw content URI strings must never be used as directory names or file names.
* The extension is derived from the accepted MIME type: `image/png` becomes `.png`, `image/jpeg` becomes `.jpg`, and `image/webp` becomes `.webp`.
* One preserved original exists per match in this version.
* Temporary files use a distinct temporary suffix in the same match directory and are never treated as the preserved file until the atomic transition succeeds.

### Atomic copy and replacement

* Create the match directory if needed, stream the source URI into a temporary file, flush and close it, and atomically rename or move it to the deterministic original path within the same directory.
* A failed copy or atomic transition must remove only the temporary file when safe and must leave the previous preserved file untouched.
* Draft replacement writes the new file successfully before removing the prior preserved file or stale known-format sibling files.
* Cleanup may delete only files under the deterministic app-private directory owned by the current tournament and match; it must never delete an arbitrary URI-derived path.
* If cleanup of an old file fails after the new file is safely installed, retain the new file, report controlled cleanup failure, and do not crash.

### Draft and finalized behavior

* A draft match may preserve one linked screenshot and explicitly replace it with a newly validated, non-duplicate linked candidate.
* Replacement must not leave stale files when safe deterministic cleanup succeeds.
* Finalized matches remain protected and cannot preserve, replace, or bypass the existing finalized-match rules through this workflow.
* Preservation and replacement must not change placements, kills, totals, scoring, standings, finalization state, or correction audit history.

### Unlink and cleanup behavior

* Unlinking a draft match clears the in-session screenshot link.
* When a deterministic local preserved file exists, unlink attempts to delete only that match-owned file and known temporary files.
* A missing file is treated as already clean.
* A deletion failure clears the in-session link but exposes a controlled cleanup error and may retain an in-session cleanup-pending path for a safe retry.
* Finalized matches do not allow unlink-driven deletion or replacement through this workflow.
* No image bytes are deleted outside the app-private directory derived from the current context.

### Persistence boundary

The v0.7.4 implementation does not add durable Room reference data. The current Room schema contains match and synchronization records but no screenshot file-reference field or screenshot metadata model. Adding such a field, entity, DAO, migration, and restore contract would establish the broader metadata boundary planned for v0.7.6.

Accordingly:

* The local file is app-owned once copied, but its reference and preservation status remain in-session presentation/application state in v0.7.4.
* Do not add Room entities, columns, DAOs, migrations, converters, or repository persistence for the preserved file.
* A deterministic path allows safe same-session replacement and cleanup without storing URI values.
* A file that outlives the presentation session without a durable reference is a documented limitation; later metadata work must define discovery, retention, restart recovery, and migration behavior.
* Do not add Supabase Storage, cloud metadata, screenshot sync, or upload queues.

## 5. Architecture Boundaries

* Match Review UI presents preservation progress, success, replacement, cleanup, and controlled error states.
* The Match Review ViewModel coordinates validation/link eligibility, active tournament and match context, preservation status, replacement, and unlink actions.
* A small testable local image preservation component may be introduced at the presentation/application boundary.
* The component may use Android app-private file APIs and an injected content-stream opener so tests do not launch Photo Picker or require real screenshots.
* Source copying must stream bytes off the main thread and must not retain the entire image in memory.
* Atomic file installation and cleanup must be isolated from scoring, match-result, synchronization, OCR, and repository logic.
* Room changes are not approved for v0.7.4 because the current architecture lacks a safe minimal reference model; any later Room proposal requires a new decision.
* Existing tournament, roster, match-processing, scoring, standings, authentication, persistence, synchronization, duplicate-detection, and correction behavior remains unchanged.

## 6. UI Behavior

* A linked, non-duplicate candidate shows preservation progress while source bytes are copied.
* Successful preservation shows clear locally-preserved confirmation for the active match.
* A draft match can select, validate, duplicate-check, link, and preserve a replacement; the previous local file remains until the replacement is installed successfully.
* A preservation or replacement failure leaves the last confirmed preserved file and link state unchanged where possible and presents a retryable controlled error.
* Unlink returns the screen to the unlinked state and reports cleanup failure separately when deletion cannot complete.
* Finalized matches show protected feedback and no active preserve or replace action.
* Picker cancellation preserves the prior selection, validation, duplicate, link, and preservation state.

## 7. Error and Cancellation Behavior

Controlled UI feedback is required for:

* Missing tournament ID.
* Missing match ID.
* Source content read failure.
* Local copy or stream failure.
* Temporary-file creation failure.
* Atomic rename or move failure.
* Replacement failure.
* Cleanup or deletion failure.
* Attempted preservation or replacement on a finalized match.

Additional rules:

* A null or cancelled Photo Picker result is not an error and preserves existing state.
* Invalid, duplicate, or unlinked candidates cannot be preserved.
* The app must not crash for unreadable source content, file-system failure, failed cleanup, missing context, or finalized-match attempts.
* A failed first preservation must not be reported as successful.
* A failed replacement must not discard the previous preserved file or link.
* Errors must not log image bytes, private URI values, credentials, or sensitive screenshot content unnecessarily.

## 8. Security and Permission Rules

* Use only the Android system Photo Picker content URI and app-private internal storage.
* Do not request camera, storage, media-library, broad filesystem, or any other new Android permission.
* Do not expose preserved files through external storage, public URIs, sharing providers, or cloud services in v0.7.4.
* Do not use a user-selected URI as a durable path or trust it as a filesystem path.
* Do not log source bytes, image contents, private URIs, or local file contents unnecessarily.
* No credentials, backend access, Supabase Storage configuration, synchronization, or network behavior is added.

## 9. Explicit Exclusions

v0.7.4 does not implement:

* Supabase Storage upload.
* Cloud screenshot metadata or screenshot metadata synchronization.
* Durable Room screenshot references, entities, schema changes, or migrations.
* OCR, score extraction, or image processing.
* Duplicate-detection changes beyond consuming the existing v0.7.3 result.
* Multiple screenshots per match.
* Image editing or mutation.
* Export behavior.
* New Android permissions.
* Cross-device restoration or long-term cloud backup.
* Public or external file sharing.

## 10. Automated Acceptance Criteria

Future implementation is acceptable only when automated coverage verifies all of the following:

* A valid, linked, non-duplicate screenshot is copied into app-private storage.
* The preserved path is deterministic and scoped to the correct tournament and match IDs without using the source URI as a file name.
* The original source bytes are preserved without image mutation.
* A preserved file is installed atomically and partial temporary files are not reported as successful files.
* A draft replacement installs the new file before safely cleaning the previous preserved file.
* A failed replacement retains the previous preserved file and link state.
* Draft unlink clears the in-session link and removes or safely marks only the deterministic match-owned file for cleanup.
* Cleanup failure produces controlled UI feedback and does not crash.
* Source-read, copy, temporary-write, and atomic-move failures produce controlled UI feedback.
* Missing tournament or match context blocks preservation safely.
* Finalized-match protection remains enforced for preservation and replacement.
* No scoring, standings, correction, authentication, synchronization, OCR, Supabase, or cloud behavior changes.
* No new Android permissions are added.
* No Room schema or migration changes are introduced in v0.7.4.
* Existing v0.7.0 Photo Picker, v0.7.1 validation, v0.7.2 linking, and v0.7.3 duplicate-detection behavior remains valid.

Required future implementation verification:

* Focused local preservation component tests.
* Focused Match Review ViewModel tests.
* Focused Match Review Compose UI tests.
* Manifest permission test.
* Focused Room tests only if a later decision explicitly approves Room persistence.
* `gradlew.bat testDebugUnitTest`.
* `gradlew.bat lintDebug`.
* `gradlew.bat assembleDebug`.
* `gradlew.bat assembleDebugAndroidTest`.
* `git diff --check`.

No manual verification instructions are part of this decision gate.

## 11. Expected Implementation Branch Name

`feature/v0.7.4-local-image-preservation`

## 12. Completion Conditions

The v0.7.4 implementation is complete only when a validated, non-duplicate, linked screenshot is copied byte-for-byte into the deterministic app-private path for the active draft match, atomic replacement and safe unlink cleanup are covered by automated tests, finalized protection and all prior Phase 7 behaviors remain valid, and the required verification passes. The implementation must remain local and app-private, with no durable Room metadata, cloud storage, OCR, synchronization, image mutation, export, or permission behavior.
