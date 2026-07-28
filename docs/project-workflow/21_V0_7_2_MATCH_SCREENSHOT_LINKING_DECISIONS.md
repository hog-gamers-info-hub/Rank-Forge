# V0.7.2 Match Screenshot Linking Decisions

## 1. Status

Approved implementation decision gate for Phase 7 v0.7.2. This document defines how a validated temporary screenshot candidate is associated with the active match-review context without introducing screenshot storage or permanent image records.

## 2. Objective

Associate one validated screenshot candidate with the active tournament and match for the current Match Review session. v0.7.2 establishes screenshot-to-match linking only; it does not preserve image bytes, upload them, process them, or change match results.

## 3. Canonical Scope

Linking begins after v0.7.1 successfully validates the selected content URI. The association is scoped by the tournament ID and match ID already owned by the Match Review route and presentation state. No separate screenshot-management screen is introduced.

## 4. Approved Decisions

### Linking entry point

* Start linking from the existing Match Review workflow after successful image validation.
* Use the current tournament ID and match ID already owned by the Match Review route and state.
* Do not create a separate screenshot-management screen.
* Linking without a valid image candidate is blocked.

### Link cardinality

* Allow one linked screenshot reference per match in this version.
* Re-linking a draft match replaces its previous linked screenshot reference for that match.
* Multiple screenshots per match are out of scope for v0.7.2.
* A link belongs to the exact `(tournamentId, matchId)` context and must not be reused for another match.

### Persistence boundary

The v0.7.2 link is in-session only. Local persistence is deferred because:

* The current v0.7.0 URI is presentation state and has no approved persisted URI-grant lifecycle.
* The current Room match model has no screenshot-link field or screenshot association entity.
* Adding a Room field, entity, or migration would establish a new persistence contract before URI access and local image ownership are approved.
* The URI may not remain readable after process or permission-grant loss, so persisting its string alone would not be a safe restore contract.

Accordingly:

* Do not add Room entities, columns, DAOs, migrations, or repository persistence for the link.
* Keep the validated URI and its link state in Match Review presentation state for the current session.
* Do not copy image bytes into app-private storage in v0.7.2.
* Do not upload image bytes or create Supabase Storage records.
* The original content URI remains the temporary selected reference until later local-preservation work defines an owned durable representation.

### Draft and finalized match behavior

* A draft match may link a validated screenshot and replace that link explicitly.
* A finalized match must not allow screenshot linking or replacement in v0.7.2. Its existing finalized/read-only protection remains authoritative, and no new finalized-correction path is introduced for screenshot links.
* Linking, replacement, or unlinking must not modify placements, kills, totals, scoring, standings, finalized state, or correction audit history.

### Unlinking behavior

* Include an explicit remove/unlink action for a current-session link so an accidental selection can be corrected before later preservation or upload phases.
* Unlinking clears the match-screenshot association from presentation state only.
* Unlinking never deletes image bytes because v0.7.2 does not own image storage.
* Unlinking is available only where linking is allowed; finalized matches remain protected.

### Error behavior

The Match Review workflow must provide controlled UI feedback and must not crash when:

* Linking is attempted without a valid image.
* The tournament ID is missing.
* The match ID is missing.
* A future approved local-link save fails.
* A permitted link replacement fails.

Because v0.7.2 does not persist links, local-link save failure is a reserved error state rather than an implemented Room operation. It must not be silently treated as a successful link if persistence is introduced in a later approved version.

## 5. Architecture Boundaries

* Match Review UI presents link, replace, and remove actions and shows the current-session linked state.
* The Match Review ViewModel coordinates validation eligibility, link state, tournament/match identity, and controlled errors.
* No repository or Room change is introduced for v0.7.2; a repository/Room boundary may be added only under a later decision approving durable URI-link persistence.
* No domain scoring or match-result abstraction is changed.
* No Supabase, synchronization queue, image-byte copying, content hashing, or OCR behavior is added.
* Existing tournament, roster, match-processing, scoring, standings, authentication, persistence, and synchronization behavior remains unchanged.

## 6. UI Behavior

* A validated candidate can be linked only from its owning Match Review session and only for a draft match.
* Match Review visibly distinguishes a validated-but-unlinked candidate from a linked candidate.
* A successful link shows the linked state for the current tournament and match.
* Re-linking replaces the prior in-session reference after the replacement action succeeds.
* Remove/unlink clears the current-session association and returns the screen to an unlinked state.
* Invalid or missing context leaves the link unconfirmed and displays a clear error.
* Finalized Match Review remains protected and does not expose link or replacement actions in v0.7.2.

## 7. Error and Cancellation Behavior

* Picker cancellation preserves the v0.7.1 validation state and any existing current-session link.
* A validation error blocks linking and does not create a link.
* Missing tournament or match context blocks linking and reports a controlled error.
* A failed replacement leaves the previous successful current-session link unchanged.
* A failed unlink leaves the previous link unchanged; with v0.7.2 in-session state, unlink is a local presentation transition and must still be guarded against invalid state.
* No link error may crash the app or alter match-result data.

## 8. Security and Permission Rules

* Continue using the Android system Photo Picker and its least-access URI mechanism.
* Do not request camera, storage, media-library, broad filesystem, or any other new Android permission.
* Do not log private screenshot content, URI values, or link metadata unnecessarily.
* Do not expose screenshot bytes, credentials, storage configuration, or backend access in the client.
* Durable URI access, local image ownership, and controlled storage access remain deferred to later approved phases.

## 9. Explicit Exclusions

v0.7.2 does not implement:

* Duplicate screenshot detection.
* Image hashing.
* App-private image preservation or copying.
* Supabase Storage upload.
* Cloud screenshot metadata.
* OCR or score extraction.
* Multiple screenshots per match.
* Image editing or mutation.
* New Android permissions.
* Export behavior.
* Room entities, schema changes, migrations, or durable screenshot-link persistence.

## 10. Automated Acceptance Criteria

Future implementation is acceptable only when automated coverage verifies all of the following:

* A validated screenshot can be linked to the current match.
* The linked state is visible in Match Review.
* Re-linking replaces the prior link for the same draft match.
* Linking cannot proceed without a valid image.
* Link state is scoped to the correct tournament and match IDs.
* Draft-match behavior follows the documented replace rule.
* Finalized-match behavior remains protected and does not permit silent linking or replacement.
* Unlink clears the current-session association without deleting image bytes.
* Missing context and link/replacement failures produce controlled UI errors.
* No scoring, standings, correction, authentication, synchronization, OCR, Supabase, or image-storage behavior changes.
* No new Android permissions are added.
* Existing v0.7.0 Photo Picker and v0.7.1 validation behavior remains valid.

Required future implementation verification:

* Focused linking ViewModel tests.
* Focused Match Review UI tests.
* Focused repository/Room tests only if a later decision approves local persistence.
* Manifest permission test.
* `gradlew.bat testDebugUnitTest`.
* `gradlew.bat lintDebug`.
* `gradlew.bat assembleDebug`.
* `gradlew.bat assembleDebugAndroidTest`.
* `git diff --check`.

## 11. Expected Implementation Branch Name

`feature/v0.7.2-match-screenshot-linking`

## 12. Completion Conditions

v0.7.2 implementation is complete only when a validated candidate can be linked, replaced, and unlinked according to this document within the correct draft Match Review context, all controlled errors and finalized protections are covered by automated tests, and the required verification passes. The implementation must remain in-session and must not introduce durable screenshot persistence, image storage, cloud behavior, OCR, hashing, export, permissions, or match-result changes.
