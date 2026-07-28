# V0.7.3 Duplicate Screenshot Detection Decisions

## 1. Status

Approved implementation decision gate for Phase 7 v0.7.3. This document defines duplicate detection for validated screenshot candidates in the existing Match Review workflow before implementation begins.

## 2. Objective

Detect when a validated screenshot candidate has already been linked during the current tournament session. Duplicate detection prevents the same source image from being silently linked to more than one match while keeping all screenshot state temporary and presentation-scoped.

## 3. Canonical Scope

v0.7.3 operates on validated image candidates selected through the Android Photo Picker and handled by Match Review. Detection runs after v0.7.1 validation and before or during the v0.7.2 link action. It uses the tournament ID and match ID already owned by the Match Review route and presentation state. No separate screenshot-management screen is introduced.

Detection is limited to the current in-memory tournament session. It is not a cross-tournament, cross-account, cross-device, or app-restart duplicate service.

## 4. Approved Decisions

### Detection entry point

* Run duplicate detection after successful image validation and before a screenshot link is confirmed.
* Use the active tournament and match context from Match Review.
* Keep selection, validation, fingerprint, and duplicate status in temporary presentation/application state.
* Do not create a separate screenshot-management screen.

### Canonical duplicate basis

The approved baseline fingerprint is a deterministic SHA-256 digest of the selected image's exact source-byte stream:

* Read the validated URI through Android content APIs such as `ContentResolver.openInputStream`.
* Feed the stream incrementally into SHA-256; do not load the complete image or complete byte array into memory.
* Compare the resulting digest rather than comparing URI strings. Different URI values that expose identical source bytes therefore compare as duplicates.
* The digest is content identity for this version. It is not a perceptual or visual-similarity hash; re-encoded or otherwise byte-different images are not considered duplicates by this baseline.
* Fingerprinting is an in-session operation only. The digest must not be written to Room, Supabase, a file, a sync queue, or screenshot metadata.

This is the minimum approved hashing mechanism for v0.7.3 because exact source-byte identity is deterministic, URI-independent, streamable, and sufficient to prevent accidental reuse without introducing image normalization, OCR, or storage ownership. Later versions may define a different approved identity model if visual equivalence is required.

### Detection scope and registry

* Maintain an in-memory registry for the current tournament session that associates each fingerprint with the match ID to which it is linked.
* A fingerprint linked to one match in a tournament is considered a duplicate when another match in that same tournament attempts to link it.
* Do not compare fingerprints across tournaments.
* Keep the registry at the presentation/application session boundary needed by Match Review so separate match screens in the same tournament can consult the same current-session state.
* Clear the registry when the tournament session ends or the owning presentation session is discarded. Losing the registry on process death or app restart is expected and remains out of scope.

### Same-match duplicate rule

Selecting or linking a fingerprint already linked to the same draft match is a controlled no-op:

* Keep the existing in-session link unchanged.
* Do not create a second link or duplicate registry entry.
* Show an informational duplicate/no-op state when user feedback is needed.

Selecting a different URI with identical source bytes for the same match follows the same no-op rule. A replacement is performed only when a new, non-duplicate fingerprint is explicitly accepted for that draft match.

### Different-match duplicate rule

* A fingerprint already linked to another match in the same tournament produces a controlled duplicate warning or error.
* Do not link the candidate silently.
* Preserve the current match's previous successful link when a replacement attempt is rejected.
* Do not alter either match's placement, kills, totals, scoring, standings, finalization, or correction history.

### Link and unlink transitions

* A non-duplicate validated candidate links normally through the existing Match Review action.
* Replacing a draft match link removes the old fingerprint association and registers the new fingerprint only after the new link transition succeeds.
* If fingerprinting, duplicate evaluation, or replacement state transition fails, retain the previous link and registry state.
* Unlinking removes only the current-session link and its registry association; it does not delete or copy image bytes. After unlinking, the same candidate may be linked again according to the normal duplicate rules.

### Finalized matches

* Finalized matches remain protected by the existing Match Review rules.
* Finalized-match protection is evaluated before duplicate registration or link replacement.
* A finalized match cannot link, replace, or bypass duplicate detection through another action.
* No scoring, standings, finalization, or correction-audit behavior changes.

## 5. Architecture Boundaries

* Match Review UI presents the link, replacement, unlink, progress, informational, and duplicate-error states.
* The existing Match Review ViewModel or presentation state coordinates validation eligibility, current context, fingerprint status, duplicate status, and link transitions.
* A small testable presentation/application-level fingerprint or duplicate-detector component may be introduced if needed.
* The detector may depend on an injected content-stream reader and digest implementation so it can be tested without launching Photo Picker.
* Fingerprinting must stream source bytes and must not decode or retain a full bitmap merely to compare content.
* A tournament-session registry may be held by the presentation/application layer; no domain or repository abstraction is required solely for this temporary state.
* Do not add Room entities, columns, DAOs, migrations, or persistence for fingerprints or links.
* Do not add Supabase records, storage objects, metadata, or synchronization queue behavior.
* Do not copy image bytes into app-private storage.
* Existing tournament, roster, match-processing, scoring, standings, authentication, persistence, synchronization, and correction behavior remains unchanged.

## 6. UI Behavior

* A validated, non-duplicate candidate remains eligible for the existing link action.
* Fingerprinting may show a bounded in-progress state; repeated link actions must not create concurrent fingerprint or link transitions.
* A successful non-duplicate link shows the existing linked confirmation.
* A same-match duplicate shows an explicit informational no-op state and leaves the existing link unchanged.
* A duplicate for another match in the same tournament shows a clear warning/error and leaves the candidate unlinked.
* A failed replacement keeps the prior linked state visible.
* Unlink returns the screen to the validated-but-unlinked state when the validated candidate remains available.
* Finalized matches expose no active link or replacement action, or show the existing protected-match feedback.
* Picker cancellation preserves the v0.7.0/v0.7.1 selection and the v0.7.2 current-session link state.

## 7. Error and Cancellation Behavior

Controlled UI feedback is required for:

* Fingerprint or content-stream read failure.
* Duplicate detected for another match in the current tournament.
* Conflicting registry state, such as one fingerprint associated with multiple matches.
* Missing tournament context.
* Missing match context.
* Attempted link or replacement on a finalized match.

Additional rules:

* A null or cancelled picker result is not an error and preserves existing state.
* Invalid or unvalidated candidates cannot enter duplicate registration.
* A duplicate-detection or link-transition failure must not crash the app.
* Failed replacement or unlink transitions preserve the last confirmed link and registry association.
* Errors must not expose private screenshot bytes or unnecessarily log URI values or fingerprints.

## 8. Security and Permission Rules

* Continue using the Android system Photo Picker and its least-access content-URI mechanism.
* Read source bytes only through the selected URI's Android content APIs.
* Do not request camera, storage, media-library, broad filesystem, or any other new Android permission.
* Do not persist, upload, synchronize, or log screenshot bytes, fingerprints, or private URI data.
* SHA-256 is used only as an in-session equality fingerprint; it is not an authorization credential or a cloud identity.
* No credentials, backend access, storage configuration, or network behavior is added.

## 9. Explicit Exclusions

v0.7.3 does not implement:

* App-private image preservation or copying.
* Supabase Storage upload or cloud screenshot metadata.
* Room entities, schema changes, migrations, or durable fingerprint/link persistence.
* OCR, score extraction, or image processing.
* Multiple screenshots per match.
* Image editing, cropping, rotation, compression, resizing, or mutation.
* Export behavior.
* New Android permissions.
* Cross-tournament, cross-account, or cross-device duplicate detection.
* Duplicate detection after app restart unless a later approved architecture safely supports it.
* Perceptual or visual-similarity matching of differently encoded images.

## 10. Automated Acceptance Criteria

Future implementation is acceptable only when automated coverage verifies all of the following:

* A validated non-duplicate screenshot links normally.
* Identical source bytes exposed by different URI strings produce the same deterministic fingerprint.
* A screenshot already linked to another match in the same tournament is detected and is not silently linked.
* Same-match duplicate selection follows the documented no-op rule.
* Replacing a draft link with a non-duplicate candidate updates the in-session registry only after success.
* A rejected replacement preserves the prior link and registry state.
* Duplicate detection is scoped to the current tournament and does not reject the same fingerprint in another tournament.
* Finalized-match protection remains enforced before duplicate registration or replacement.
* Fingerprint/read failure, registry conflict, missing context, and finalized-match attempts show controlled UI feedback and do not crash.
* Unlink clears only the current-session link and registry association.
* Duplicate detection does not change placement, kills, totals, scoring, standings, finalization, correction, authentication, synchronization, OCR, Supabase, or image-storage behavior.
* No new Android permissions are added.
* Existing v0.7.0 Photo Picker, v0.7.1 validation, and v0.7.2 linking behavior remains valid.

Required future implementation verification:

* Focused duplicate detector or fingerprint tests, if a component is introduced.
* Focused Match Review ViewModel tests.
* Focused Match Review Compose UI tests.
* Manifest permission test.
* `gradlew.bat testDebugUnitTest`.
* `gradlew.bat lintDebug`.
* `gradlew.bat assembleDebug`.
* `gradlew.bat assembleDebugAndroidTest`.
* `git diff --check`.

No manual verification instructions are part of this decision gate.

## 11. Expected Implementation Branch Name

`feature/v0.7.3-duplicate-screenshot-detection`

## 12. Completion Conditions

The v0.7.3 implementation is complete only when duplicate detection follows this document for validated candidates in the current tournament session, same-match no-op and different-match rejection behavior are covered by automated tests, finalized protection and prior-version behavior remain valid, and all required verification passes. The implementation must remain temporary and in-session, with no new persistence, storage, cloud, synchronization, OCR, image mutation, export, or permission behavior.
