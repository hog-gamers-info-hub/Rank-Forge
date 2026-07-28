# V0.7.1 Image Validation Decisions

## 1. Status

Approved implementation decision gate for Phase 7 v0.7.1. This document defines validation of the temporary Photo Picker selection after v0.7.0 and before any image persistence, storage, or processing work begins.

## 2. Objective

Confirm that the temporary selected image URI identifies a usable screenshot-image candidate for later Phase 7 workflows. Validation establishes only candidate usability; it does not create a screenshot record, confirm a match association, or prepare the image for OCR.

## 3. Canonical Scope

v0.7.1 validates the temporary selected image URI produced by v0.7.0 in the existing match-review presentation workflow. It follows Photo Picker selection and precedes later validation, linking, duplicate-detection, local-preservation, storage, metadata, and OCR work.

## 4. Approved Decisions

### Validation entry point

* Run validation after a Photo Picker image selection.
* Keep validation in the existing match-review presentation workflow.
* Do not persist the selected image or validation result.
* Do not permanently link the selected image to its tournament or match.

### Accepted image candidate

A selected URI is an acceptable candidate only when all of the following conditions are met:

* Its MIME or content type is image-based.
* Its format is PNG, JPEG, or WebP when supported by Android decoding.
* Android content APIs can read the URI.
* Android can decode image dimensions without loading the full image into memory.
* Decoded width and height are positive.
* The image is not empty or unreadable.
* The image does not exceed the safe pixel-count or dimension limit defined by the implementation.

The exact safe pixel-count or dimension threshold is not fixed by this decision. The v0.7.1 implementation must define a bounded limit appropriate to its supported Android memory budget and reject candidates that exceed it.

### Rejection behavior

The match-review presentation state must reject the candidate and show controlled UI feedback for:

* A null or blank URI.
* Non-image content.
* An unsupported format.
* An unreadable URI.
* Decode failure.
* Zero or invalid dimensions.
* An image exceeding the implementation's safe pixel-count or dimension limit.

Rejecting a candidate must not crash the app, mark it as valid, or write any persistent data.

## 5. Architecture Boundaries

* A small presentation- or application-level validator may be introduced when needed.
* The validator must be testable without launching the Photo Picker.
* Validation must safely inspect content metadata and decode bounds.
* Validation must avoid full bitmap loading unless it is absolutely necessary; bounds-only decoding is the approved default.
* The selected URI and validation result remain temporary presentation state only.
* Domain and repository layers must not be introduced solely for this temporary validation.
* Existing tournament, roster, match-processing, scoring, standings, authentication, persistence, and synchronization behavior remains unchanged.

## 6. UI Behavior

* A valid image candidate shows selected and validated confirmation in the existing match-review screen.
* An invalid candidate shows a clear validation error and is not marked valid.
* Selecting a new image reruns validation and replaces the previous temporary selection and validation state with the new candidate's accepted or rejected state.
* Picker cancellation preserves the pre-existing v0.7.0 selection and validation state.
* Validation is limited to the current screen session and must survive recomposition through presentation state.

## 7. Error and Cancellation Behavior

* Picker cancellation is not a validation error.
* Null, blank, unreadable, unsupported, malformed, and oversized candidates produce controlled validation errors.
* Existing temporary state remains available after picker cancellation; a newly selected invalid candidate replaces the previous state with a rejected state.
* A validation failure must clear any in-progress validation state so the operator can select another image.
* Errors must not log image content, URI values, or private screenshot data unnecessarily.

## 8. Security and Permission Rules

* Continue using the Android system Photo Picker as the least-access selection mechanism.
* Do not add camera, storage, media-library, or broad filesystem permissions.
* Read the selected URI only through approved Android content APIs needed for metadata and bounds inspection.
* Do not copy image bytes into app-private storage or retain them outside the temporary screen state.
* Do not add credentials, backend access, storage configuration, or network behavior.

## 9. Explicit Exclusions

v0.7.1 does not implement:

* OCR or image processing.
* Cropping, rotation, compression, resizing, or other image alteration.
* Screenshot-to-match persistence.
* Duplicate screenshot detection.
* Hashing.
* Local image preservation or app-private image copying.
* Room entities or migrations.
* Supabase Storage.
* Screenshot metadata persistence.
* Upload or synchronization queue behavior.
* Manual correction workflows.
* Multiple-image selection.
* Camera capture.

## 10. Automated Acceptance Criteria

Future implementation is acceptable only when automated coverage verifies all of the following:

* A valid PNG, JPEG, or supported WebP image URI is accepted.
* A non-image URI is rejected.
* An unreadable URI is rejected.
* A decode failure is rejected.
* Invalid dimensions are rejected.
* An image over the implementation's documented safe bound is rejected.
* The validation result updates Match Review UI state.
* Re-selection replaces the previous temporary validation state with the new candidate's accepted or rejected state.
* Picker cancellation preserves the previous state.
* No new Android permissions are added.
* No Room, Supabase, OCR, synchronization, or storage changes are introduced.
* Existing v0.7.0 Photo Picker behavior remains valid.

Required future implementation verification:

* Focused validator tests.
* Focused Match Review ViewModel and Compose UI tests.
* Manifest permission test.
* `gradlew.bat testDebugUnitTest`.
* `gradlew.bat lintDebug`.
* `gradlew.bat assembleDebug`.
* `gradlew.bat assembleDebugAndroidTest`.
* `git diff --check`.

## 11. Expected Implementation Branch Name

`feature/v0.7.1-image-validation`

## 12. Completion Conditions

v0.7.1 implementation is complete only when it validates the temporary selected image URI according to this document, presents deterministic valid or rejected state in Match Review, and passes all required automated acceptance criteria and verification. The implementation must remain limited to safe temporary validation and must not introduce any excluded permission, persistence, storage, synchronization, image-alteration, duplicate-detection, or OCR behavior.
