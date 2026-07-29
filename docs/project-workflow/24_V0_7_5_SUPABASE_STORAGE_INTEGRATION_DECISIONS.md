# V0.7.5 Supabase Storage Integration Decisions

## 1. Status

Approved implementation decision gate for Phase 7 v0.7.5. This document defines how a locally preserved match screenshot becomes eligible for private Supabase Storage upload before any Android source code, Supabase migration, storage policy, or configuration is changed.

## 2. Objective

Upload the byte-for-byte local screenshot file produced by v0.7.4 to private Supabase Storage from the existing Match Review screenshot workflow. v0.7.5 adds cloud object storage only; it does not add OCR, image mutation, durable screenshot metadata, or match-result behavior.

## 3. Canonical Scope

v0.7.5 starts only after all prior screenshot steps have succeeded:

* v0.7.0 Photo Picker selection.
* v0.7.1 image validation.
* v0.7.2 match screenshot linking.
* v0.7.3 duplicate screenshot detection.
* v0.7.4 local image preservation.

The upload uses the active authenticated Supabase session, active tournament ID, active match ID, and the app-private local file path from v0.7.4. The screenshot remains one original image per match. No separate screenshot-management screen is approved unless the existing Match Review surface needs a small upload status, retry, or error area.

## 4. Approved Decisions

### Upload entry point

* Start upload only after local image preservation succeeds.
* Require the active authenticated user/session before any upload attempt.
* Require the active tournament ID and match ID before resolving the storage object path.
* Do not upload directly from the Photo Picker URI.
* Do not run upload before validation, duplicate detection, linking, and local preservation have succeeded.
* Keep upload initiation in the existing Match Review screenshot workflow.

### Storage ownership and privacy

* Store screenshots in private Supabase Storage.
* Do not make screenshot objects public.
* Do not use long-lived public URLs.
* Object access must be scoped to the authenticated owner, tournament ID, and match ID.
* Users must not read, overwrite, list, or delete screenshots owned by other users.
* Download access, if exposed in this version, must use authenticated private download or short-lived signed URLs only.
* Service-role credentials must not be used by the Android client.

### Bucket and path

The approved storage bucket is:

```text
match-screenshots
```

The bucket must be private.

The approved object path format is:

```text
users/<auth-user-id>/tournaments/<tournament-id>/matches/<match-id>/original.<extension>
```

Path rules:

* `<auth-user-id>` is the authenticated Supabase user ID from the active session.
* `<tournament-id>` is the active tournament ID from Match Review.
* `<match-id>` is the active match ID from Match Review.
* The filename is deterministic and uses the single original screenshot name for this match.
* Raw Photo Picker URI strings and local filesystem paths must never be used as object names.
* The extension is derived from the v0.7.4 local preserved MIME/format decision: PNG uploads as `.png`, JPEG uploads as `.jpg`, and WebP uploads as `.webp`.
* Upload metadata must include the matching content type: `image/png`, `image/jpeg`, or `image/webp`.

Draft replacement overwrites the deterministic object path using the Supabase Storage upsert or equivalent overwrite operation. This choice preserves the one-screenshot-per-match model without introducing versioned cloud objects or a durable metadata index before v0.7.6.

### Storage authorization and policy model

The future implementation must add the private bucket and Storage object policies only through the approved Supabase migration or configuration process for the implementation task.

The policy model must enforce all of the following:

* Authenticated users may upload only to `match-screenshots`.
* The user path segment must match the authenticated user ID.
* The object owner must match the authenticated user where Supabase Storage ownership data is available.
* The tournament path segment must identify a tournament owned by the authenticated user.
* The match path segment must identify a match in that tournament.
* Object read and overwrite are allowed only for the owning authenticated user and matching tournament/match path.
* Cross-owner read, overwrite, list, and delete are denied.
* Public access is denied.

Because overwrite/upsert requires Storage object `INSERT`, `SELECT`, and `UPDATE` access, the implementation must include narrowly scoped policies for those operations. If the implementation distinguishes object download from bucket listing, listing must remain denied or limited to the authenticated user's own path.

Cloud delete is not part of v0.7.5. Therefore no Android delete workflow or broad Storage `DELETE` policy is approved in this version.

### Upload behavior

* Upload the app-private preserved local file content byte-for-byte.
* Do not mutate, compress, resize, rotate, crop, enhance, OCR, or otherwise alter the image.
* Draft replacement uploads the replacement file to the same deterministic object path after the replacement is locally preserved.
* Finalized matches remain protected from screenshot replacement and therefore cannot trigger upload replacement through this workflow.
* Upload failure must not delete the local preserved file.
* Replacement or overwrite failure must leave the last known local state and cloud status reported honestly.

### Sync queue boundary

v0.7.5 does not use the existing persistent offline sync queue.

This decision is based on the current architecture:

* The existing queue operation types cover tournament upload, tournament restoration, draft match synchronization, finalized match synchronization, and match restoration.
* The existing queue identity is operation type plus tournament ID only.
* Screenshot upload requires authenticated user ID, tournament ID, match ID, deterministic object path, local file identity, MIME type, overwrite behavior, and eventual metadata state.
* Adding that queue identity without a screenshot metadata model would create broad queue behavior before v0.7.6 defines durable screenshot records.

Allowed v0.7.5 behavior is immediate online upload from Match Review after local preservation succeeds. If upload fails because of network, authentication, authorization, local file, or Storage errors, the app shows controlled UI feedback, keeps the local file, and allows an operator-triggered retry during the current available workflow state.

Persistent queued screenshot upload, retry exhaustion, background replay, and upload-state restoration after app restart are deferred until a later approved decision defines screenshot metadata and queue identity.

### Local and cloud state boundary

v0.7.5 persists only the cloud object in Supabase Storage. It does not persist a local Room row or Supabase database metadata row describing that object.

Upload status may be represented in Match Review presentation state for the current screen/session:

* not eligible;
* eligible after local preservation;
* uploading;
* uploaded;
* failed with controlled category.

The uploaded object path is deterministic enough to support same-session replacement, but the app must not claim durable screenshot restore, cloud metadata sync, OCR readiness metadata, or cross-device screenshot recovery until v0.7.6 or a later approved version defines the metadata model.

## 5. Architecture Boundaries

* Match Review or the existing screenshot workflow coordinates upload state after local preservation.
* A small testable storage/upload component may be introduced.
* The upload component may depend on the existing Supabase client provider and may add the Supabase Storage client plugin or dependency only if required.
* Upload work must run off the main thread.
* Upload code reads the app-private preserved file and streams or uploads that file without mutating bytes.
* Domain scoring, standings, finalization, correction audit, authentication, Room persistence, existing sync, duplicate detection, and local preservation behavior remain unchanged except for upload status interaction.
* No repository, Room entity, Room migration, or metadata model is introduced solely to persist screenshot upload status in v0.7.5.
* Storage bucket and Storage policy changes, if required by implementation, must be explicitly represented in versioned Supabase files and covered by Supabase storage policy tests.

## 6. UI Behavior

* A locally preserved screenshot shows upload eligibility in Match Review.
* Upload in progress shows a clear progress state and prevents duplicate upload requests.
* Successful upload shows private cloud-upload confirmation for the active match.
* Upload failure shows controlled UI feedback and leaves the local preserved file intact.
* Network or offline failure shows controlled feedback and allows an operator-triggered retry while the preserved file remains available.
* Draft replacement uploads the replacement file to the same deterministic object path after the replacement is locally preserved.
* Finalized matches remain protected from screenshot replacement and therefore cannot trigger upload replacement through this workflow.
* Picker cancellation preserves prior Photo Picker, validation, duplicate, link, preservation, and upload state.
* No screenshot-management screen is added.

## 7. Error and Cancellation Behavior

Controlled UI feedback is required for:

* Missing authenticated user/session.
* Missing local preserved file.
* Missing tournament ID.
* Missing match ID.
* Unsupported local preserved MIME or extension.
* Local file read failure.
* Upload failure.
* Network or offline failure.
* Authorization, RLS, or Storage policy denial.
* Replacement or overwrite failure.

Additional rules:

* Upload failure must not delete the local preserved file.
* Replacement failure must leave the previous cloud object and local preserved state reported honestly.
* Upload cancellation must clear in-progress state without reporting success.
* Delete and cloud cleanup failures are not applicable in v0.7.5 because cloud delete is not approved.
* Errors must not crash the app, expose credentials, log image bytes, or unnecessarily log private paths, object names, or screenshot content.

## 8. Security and Permission Rules

* Do not add Android storage, media-library, camera, or broad filesystem permissions.
* Use the app-private file created by v0.7.4 as the upload source.
* Upload only to the private `match-screenshots` bucket.
* Do not expose public screenshot URLs.
* Use only the authenticated user's client session for Android uploads.
* Do not include service-role keys or privileged credentials in Android.
* Do not log screenshot bytes, OCR content, private local paths, object paths, access tokens, or Supabase keys unnecessarily.
* Supabase Storage access must rely on least-privilege Storage object policies, not authentication alone.

## 9. Explicit Exclusions

v0.7.5 does not implement:

* OCR.
* Score extraction.
* Multiple screenshots per match.
* Image editing or mutation.
* Cropping, rotation, compression, resizing, or enhancement.
* Export behavior.
* Public screenshot sharing.
* Cross-account screenshot access.
* Cross-device restoration beyond the immediate uploaded object.
* Broad screenshot metadata intended for v0.7.6.
* Room screenshot metadata, Room entities, Room migrations, or local upload-status persistence.
* Supabase database schema changes except storage bucket and Storage policy changes strictly required for private object authorization.
* Persistent offline screenshot upload queue behavior.
* Cloud delete/unlink cleanup.
* New Android storage, media-library, camera, or broad filesystem permissions.

## 10. Automated Acceptance Criteria

Future implementation is acceptable only when automated coverage verifies all of the following:

* A locally preserved screenshot uploads to private Supabase Storage.
* Upload starts only after validation, duplicate detection, linking, and local preservation succeed.
* Upload requires an active authenticated Supabase session.
* Missing authenticated session is handled with controlled UI feedback.
* Missing local preserved file is handled with controlled UI feedback.
* Missing tournament ID and missing match ID are handled with controlled UI feedback.
* The object path is scoped to authenticated user ID, tournament ID, and match ID.
* The uploaded object uses the private `match-screenshots` bucket.
* The uploaded bytes match the app-private preserved file bytes.
* Upload metadata uses the correct MIME type for PNG, JPEG, and WebP.
* Upload failure preserves the local file.
* Network or offline failure shows controlled UI feedback and allows immediate retry while the file remains available.
* Draft replacement overwrites the deterministic object path according to this document.
* Replacement or overwrite failure shows controlled UI feedback and does not report success.
* Finalized-match protection remains enforced before upload replacement.
* Unauthorized cross-owner access is blocked by Storage ownership rules.
* No scoring, standings, correction, OCR, image mutation, export, public sharing, Room metadata, or existing sync behavior changes.
* No new Android storage, media-library, camera, or broad filesystem permissions are added.
* Existing v0.7.0 Photo Picker, v0.7.1 image validation, v0.7.2 match linking, v0.7.3 duplicate detection, and v0.7.4 local preservation behavior remains valid.

Required future implementation verification:

* Focused storage/upload component tests.
* Focused Match Review or ViewModel upload-state tests.
* Supabase bucket and Storage policy tests if bucket or policy files are introduced.
* Manifest permission test.
* `gradlew.bat testDebugUnitTest`.
* `gradlew.bat lintDebug`.
* `gradlew.bat assembleDebug`.
* `gradlew.bat assembleDebugAndroidTest`.
* `git diff --check`.

No manual verification instructions are part of this decision gate.

## 11. Expected Implementation Branch Name

`feature/v0.7.5-supabase-storage-integration`

## 12. Completion Conditions

The v0.7.5 implementation is complete only when a v0.7.4 locally preserved screenshot uploads byte-for-byte to the private `match-screenshots` bucket at the approved authenticated user, tournament, and match scoped object path; overwrite replacement follows the documented draft-match rule; controlled errors and private Storage authorization are covered by automated tests; and all required verification passes.

The implementation must remain limited to Supabase Storage integration and upload status. It must not introduce OCR, image mutation, public sharing, persistent screenshot metadata, Room migrations, broad sync queue behavior, cloud delete behavior, export behavior, or new Android permissions.
