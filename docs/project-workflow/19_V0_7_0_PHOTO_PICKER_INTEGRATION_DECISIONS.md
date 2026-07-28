# V0.7.0 Photo Picker Integration Decisions

## 1. Status

Approved implementation decision gate for Phase 7 v0.7.0. This document defines the Photo Picker integration boundary before production implementation begins.

## 2. Objective

Allow an operator to select one scoreboard screenshot through the Android system Photo Picker from the existing match-specific workflow. The selected image is temporary presentation state only; no screenshot record, processing, validation, or storage behavior is introduced in this version.

## 3. Canonical Scope

v0.7.0 implements only the entry into the Android system Photo Picker and the temporary display of a selected image URI in the existing match review workflow. It is the first Phase 7 increment in the roadmap and does not advance into later screenshot, storage, or OCR versions.

## 4. Approved Decisions

### Entry point

* Launch the picker from the existing match-specific workflow.
* Use the current match review screen, which already owns the selected tournament and match context, as the implementation entry point.
* Do not add a separate screenshot-management screen in v0.7.0.

### Picker behavior

* Use the Android system Photo Picker.
* Restrict selection to images.
* Select one screenshot for each picker launch.
* Do not request camera, storage, media-library, or broad filesystem permissions.
* Do not implement a custom file browser.

### Selection state

* Keep the selected content URI in presentation state for the current match-review screen session.
* The state must survive Compose recomposition.
* Selecting another image replaces the existing temporary selection.
* Cancelling the picker preserves the existing selection state.
* The screen must show a clear selected-image state or preview sufficient to confirm that a selection succeeded.

## 5. Architecture Boundaries

* Compose UI owns the system Photo Picker launcher and receives its result.
* The Match Review ViewModel, or its existing presentation state, owns the temporary selected URI and picker-related UI state.
* Domain and repository layers must not be introduced merely to hold temporary picker selection.
* The URI must not be represented as a permanent local or cloud record in v0.7.0.
* Existing tournament, roster, match-processing, scoring, standings, authentication, persistence, and synchronization behavior remains unchanged.

## 6. UI Behavior

* The match review screen exposes a picker action only within the existing match-specific workflow and retains its current tournament and match context.
* While a picker request is active, repeated action taps must not start another request.
* A successful image result updates the presentation state and visibly replaces any prior temporary selection.
* The selected-image indication remains available for the screen session after recomposition.

## 7. Error and Cancellation Behavior

* Picker cancellation is not an error and leaves the existing state unchanged.
* An empty result must not crash the app and must preserve the existing selection.
* Unexpected picker-launch or returned-URI failures must produce a controlled UI error state.
* A failure must not create, alter, upload, or synchronize any persistent screenshot data.
* The active-request state must be cleared when the picker result or a controlled launch failure is handled, so the operator can try again.

## 8. Security and Permission Rules

* Use the Android system Photo Picker as the least-access selection mechanism.
* Do not add Android permissions for camera, storage, media-library, or broad filesystem access.
* Do not log image content, content URIs, or other private screenshot data unnecessarily.
* No credentials, secrets, storage configuration, or backend access are required or added for this version.

## 9. Explicit Exclusions

The following are not implemented in v0.7.0 and remain deferred to later Phase 7 versions where applicable:

* Image validation.
* Resolution or file-size checks.
* Orientation handling.
* Screenshot-to-match persistence.
* Duplicate screenshot detection.
* Image hashing.
* App-private image copying.
* Room entities or migrations.
* Supabase Storage.
* Screenshot metadata persistence.
* Upload or sync queue operations.
* OCR or image processing.
* Camera capture.
* Multiple-image selection.
* A custom file browser or a separate screenshot-management screen.

## 10. Automated Acceptance Criteria

Future implementation is acceptable only when automated coverage verifies all of the following:

* The picker action launches the image-only Android system Photo Picker for one image.
* A selected URI updates the match-review screen state.
* A second selection replaces the first URI.
* Picker cancellation preserves the current state.
* Empty or invalid results do not crash the app and expose a controlled state where applicable.
* Repeated taps cannot create concurrent picker launches.
* Existing navigation and match workflow tests remain valid.
* No new Android permissions are added.
* No Room or Supabase schema changes are introduced.

Required future implementation verification:

* Relevant unit tests.
* Relevant Compose UI tests.
* `gradlew.bat testDebugUnitTest`.
* `gradlew.bat lintDebug`.
* `gradlew.bat assembleDebug`.
* `gradlew.bat assembleDebugAndroidTest`.
* `git diff --check`.

## 11. Expected Implementation Branch Name

`feature/v0.7.0-photo-picker-integration`

## 12. Completion Conditions

v0.7.0 implementation is complete only when the approved picker behavior and all automated acceptance criteria in this document are satisfied, the required verification passes, and the change remains limited to temporary Photo Picker selection in the existing match-review workflow. It must not introduce any excluded persistence, storage, processing, permission, or navigation behavior.
