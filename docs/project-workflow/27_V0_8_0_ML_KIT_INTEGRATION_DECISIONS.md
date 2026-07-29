# v0.8.0 — ML Kit Integration Decisions

## 1. Status

Approved implementation decision gate for Phase 8 v0.8.0. Phase 7 is formally complete and closed. Phase 8 implementation has not started.

This document authorizes only the v0.8.0 ML Kit integration foundation after review and merge. It does not authorize OCR parsing, screenshot evaluation, or work assigned to later roadmap versions.

## 2. Version scope

v0.8.0 establishes the project-owned boundary for on-device text recognition of already approved screenshot inputs. The implementation scope is limited to:

* a version-catalog entry for the approved ML Kit dependency;
* the corresponding app-module dependency;
* an injectable recognizer factory or adapter boundary owned by the project; and
* a minimal smoke-level wrapper around ML Kit text recognition.

The integration must use Google ML Kit Text Recognition v2 with the bundled Latin model only. It must not define screenshot layout, image preparation, raw-result persistence, parsing, review, correction, scoring, or finalization behavior.

## 3. Canonical sources reviewed

This decision was reviewed against:

* `AGENTS.md` and `README.md`;
* `docs/project-workflow/00_PHASE_AND_VERSION_ROADMAP.md`;
* `docs/project-workflow/26_PHASE_7_CLOSURE_AUDIT.md`;
* `docs/project-workflow/19_V0_7_0_PHOTO_PICKER_INTEGRATION_DECISIONS.md` through `docs/project-workflow/25_V0_7_6_SCREENSHOT_METADATA_DECISIONS.md`;
* `docs/02_SYSTEM_ARCHITECTURE.md`, `docs/04_OCR_AND_TEAM_MATCHING.md`, and `docs/06_ANDROID_APP.md`;
* `docs/09_TESTING_AND_ACCEPTANCE.md` and `docs/11_SECURITY_AND_PRIVACY.md`;
* the applicable `docs/ai/` workflow, coding, security, testing, Git, and approval documents; and
* the existing Android dependency catalog, app dependency declarations, and test structure.

The roadmap assigns only bundled Latin Text Recognition v2 integration to v0.8.0. Phase 7 screenshot intake and storage remain the established upstream boundary.

## 4. Approved ML Kit dependency decision

* Use Google ML Kit Text Recognition v2 through the bundled dependency `com.google.mlkit:text-recognition:16.0.1`.
* Use the bundled Latin model only. The model is statically linked with the app dependency.
* Do not use the Google Play services or unbundled dependency `com.google.android.gms:play-services-mlkit-text-recognition`.
* Do not add AndroidManifest ML Kit dependency metadata for install-time model download, because no model download is required for the bundled model.
* Create the dependency-catalog entry and add only that catalog alias to the app module when implementation is approved.
* Use `LatinTextRecognizerOptions.DEFAULT_OPTIONS` for the v0.8.0 recognizer configuration.

No alternative model, language configuration, cloud OCR service, external AI service, or runtime model-delivery behavior is approved in this version.

## 5. Architecture boundary decision

OCR execution must be isolated behind a project-owned, injectable recognizer factory or adapter boundary. The boundary may own creation of a recognizer configured with `LatinTextRecognizerOptions.DEFAULT_OPTIONS` and a minimal wrapper that invokes ML Kit text recognition.

The adapter boundary must allow future approved work to add preprocessing, raw block preservation, parsing, and review handling without replacing the ML Kit integration. It must not place ML Kit calls in composables, scoring, standings, persistence, synchronization, or finalization code.

The wrapper may return or propagate the unmodified ML Kit recognition outcome needed to prove the integration works. It must not interpret, normalize, persist, or confirm OCR content in v0.8.0.

## 6. OCR behavior allowed in v0.8.0

The only OCR behavior allowed is smoke-level, on-device text-recognition invocation through the approved project-owned boundary using the bundled Latin recognizer.

Allowed implementation behavior is limited to dependency wiring, recognizer creation, invocation, and controlled propagation of success or failure to the caller. It may operate on an image input supplied by an existing approved screenshot workflow, but it must not define new screenshot-selection, storage, or routing behavior.

No result is a confirmed match result. v0.8.0 does not preserve raw blocks or lines, create OCR processing records, or produce reviewable candidate fields.

## 7. Explicit exclusions

v0.8.0 must not:

* define scoreboard crop coordinates or a supported fixed scoreboard layout;
* implement image preprocessing, including cropping, scaling, contrast adjustment, enhancement, rotation, compression, or image mutation;
* parse placements, player names, kills, teams, totals, or standings;
* preserve raw OCR blocks, lines, confidence-related metadata, parsed values, or review state;
* modify finalized-match protection, scoring, standings, correction workflows, Room persistence, Supabase synchronization, or screenshot-storage behavior;
* add Room entities, migrations, Supabase schema changes, Storage changes, sync-queue changes, Android permissions, manifest model-download metadata, cloud OCR, or external AI services; or
* evaluate recognition accuracy against real screenshots.

Existing Phase 7 validation, duplicate detection, local preservation, private storage, metadata, and finalized-match protections remain unchanged. Real screenshot evaluation is deferred to the roadmap's existing v0.8.8.

## 8. Testing and verification expectations

Future v0.8.0 implementation tests must verify:

* the approved catalog and app-module dependency wiring;
* recognizer construction through the project-owned adapter or factory using `LatinTextRecognizerOptions.DEFAULT_OPTIONS`;
* wrapper success behavior using test doubles where possible; and
* controlled propagation of recognizer failures without requiring real screenshot accuracy.

Tests must not treat synthetic, fake, or ad hoc screenshots as OCR-accuracy evidence. Real screenshot evaluation, ground-truth comparison, and accuracy acceptance are deferred to v0.8.8. Implementation verification must remain limited to the approved change and include `git diff --check` with the relevant future unit and Android checks.

## 9. Risks and mitigations

| Risk | Approved mitigation |
| --- | --- |
| A platform-specific ML Kit API leaks into unrelated application logic. | Keep recognition creation and invocation behind the project-owned injectable adapter or factory. |
| The bundled model is confused with a download-dependent model. | Use only `com.google.mlkit:text-recognition:16.0.1`; do not add the Play services dependency or manifest download metadata. |
| Smoke-level text output is mistaken for validated scoreboard data. | Do not parse, persist, normalize, review, score, synchronize, or finalize OCR output in this version. |
| Tests imply OCR accuracy without approved evidence. | Test wiring, adapter behavior, and failure propagation with doubles; defer real screenshot evaluation to v0.8.8. |

## 10. Acceptance criteria for implementation

v0.8.0 implementation is acceptable only when:

* the dependency catalog and app module use `com.google.mlkit:text-recognition:16.0.1`;
* no Google Play services or unbundled ML Kit text-recognition dependency is added;
* no AndroidManifest ML Kit model-download metadata is added;
* recognition is configured with `LatinTextRecognizerOptions.DEFAULT_OPTIONS`;
* a project-owned injectable recognizer factory or adapter and minimal smoke-level wrapper exist;
* success and failure behavior are covered through test doubles where possible;
* no parsing, preprocessing, crop coordinates, raw-result preservation, review, correction, scoring, persistence, synchronization, screenshot-storage, or finalized-match behavior changes are introduced; and
* the relevant future verification passes, including `git diff --check`.

## 11. Next implementation action

After this decision document is reviewed and merged, the next approved implementation task is limited to adding the bundled ML Kit dependency and the isolated smoke-level recognizer integration described here. It must not begin any excluded Phase 8 work.
