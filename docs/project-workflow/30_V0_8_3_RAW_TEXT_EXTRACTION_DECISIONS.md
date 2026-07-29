# v0.8.3 — Raw Text Extraction Decisions

## 1. Status

Approved implementation decision gate for Phase 8 v0.8.3. Phase 7 is complete and closed, and v0.8.0 ML Kit Integration, v0.8.1 Fixed Scoreboard Layout Definition, and v0.8.2 Image Preprocessing are complete and merged.

This document authorizes only raw OCR text extraction after review and merge. It does not authorize parsing, semantic interpretation, persistence, review UI, result processing, or any work assigned to later roadmap versions.

## 2. Version scope

v0.8.3 adds a project-owned raw extraction boundary that invokes the existing bundled Latin ML Kit integration for OCR-ready preprocessing candidates and preserves the returned OCR hierarchy without interpreting it as scoreboard data.

The implementation scope is limited to:

* accepting one or more ordered v0.8.2 preprocessing candidates;
* invoking ML Kit through the existing project-owned v0.8.0 boundary or its minimum required raw-result extension;
* mapping ML Kit's raw text hierarchy and available metadata into project-owned models;
* returning ordered extraction outcomes that retain source-candidate metadata; and
* controlled, typed handling of empty, incomplete, or failed extraction.

The output is raw OCR evidence only. It is not a parsed placement, player name, kill, team, score, standing, confidence decision, or confirmed match result.

## 3. Canonical sources reviewed

This decision was reviewed against:

* `AGENTS.md` and `README.md`;
* `docs/project-workflow/00_PHASE_AND_VERSION_ROADMAP.md`;
* `docs/project-workflow/26_PHASE_7_CLOSURE_AUDIT.md`;
* `docs/project-workflow/27_V0_8_0_ML_KIT_INTEGRATION_DECISIONS.md`, `docs/project-workflow/28_V0_8_1_FIXED_SCOREBOARD_LAYOUT_DECISIONS.md`, and `docs/project-workflow/29_V0_8_2_IMAGE_PREPROCESSING_DECISIONS.md`;
* `docs/02_SYSTEM_ARCHITECTURE.md`, `docs/04_OCR_AND_TEAM_MATCHING.md`, and `docs/06_ANDROID_APP.md`;
* `docs/09_TESTING_AND_ACCEPTANCE.md` and `docs/11_SECURITY_AND_PRIVACY.md`;
* `docs/ai/00_AI_WORKFLOW.md`; and
* the current v0.8.0 recognizer and ML Kit factory, v0.8.1 layout models, v0.8.2 preprocessing models and bitmap adapter, and relevant Phase 7 image-validation and preservation boundaries.

The roadmap assigns raw text extraction to v0.8.3. Parsing begins no earlier than v0.8.4, and real screenshot evaluation remains deferred to v0.8.8.

## 4. Relationship to v0.8.0 ML Kit integration

v0.8.3 must use the bundled Latin ML Kit Text Recognition v2 integration through the project-owned v0.8.0 boundary. It must not add an alternative OCR dependency, unbundled model, cloud OCR service, external AI service, or direct ML Kit call from a UI, ViewModel, Room, Supabase, or parser class.

The current v0.8.0 `OcrTextRecognizer` contract exposes only full recognized text. Therefore, v0.8.3 may add the minimum sibling raw-extraction interface and ML Kit adapter/engine extension needed to expose blocks, lines, elements, and metadata. It must reuse the existing ML Kit recognizer factory/configuration and close the recognizer according to the established v0.8.0 lifecycle. The existing smoke-level recognizer contract, binding, failure behavior, and dependency wiring remain unchanged.

## 5. Relationship to v0.8.1 fixed layout

v0.8.1 remains the sole supported layout definition and the source of its normalized geometry. v0.8.3 does not create crop coordinates, a second layout, row boundaries, or field semantics.

Raw extraction may retain ML Kit geometry for the candidate image, but geometry is evidence only. It must not be joined to a layout row, interpreted as a placement/player/kill region, or used to infer scoreboard values in v0.8.3.

## 6. Relationship to v0.8.2 preprocessing

Raw extraction may consume the ordered in-memory candidates produced by v0.8.2. Each extraction outcome must preserve the complete source-candidate metadata needed to identify its preprocessing order, crop, pixel rectangle, applied steps, and scale factor.

The extractor must preserve candidate order. For an ordered input set, it returns an ordered extraction outcome for each supplied candidate, including an empty or failed outcome when applicable. It must not select a candidate based on OCR content, replace candidate metadata, mutate a candidate image, or persist processed candidates.

## 7. Raw OCR extraction boundary decision

Raw extraction must be isolated behind project-owned abstractions. The approved default package boundary is:

* domain models and interfaces under `app/src/main/java/com/hoggamers/rankforge/domain/ocr/extraction/`; and
* data/ML Kit implementation under `app/src/main/java/com/hoggamers/rankforge/data/ocr/extraction/`.

The implementation may use the following focused names, adjusted only to match established project naming conventions:

* `RawOcrTextExtractor`;
* `RawOcrExtractionInput`;
* `RawOcrExtractionResult`;
* `RawOcrBlock`;
* `RawOcrLine`;
* `RawOcrElement`;
* `RawOcrGeometry`;
* `RawOcrConfidence`;
* `RawOcrExtractionFailure`; and
* `MlKitRawOcrTextExtractor`.

The domain-facing contract must accept preprocessing candidates through project-owned types and return project-owned raw models. Android and ML Kit types must not leak into domain models, UI state, persistence entities, or parser interfaces.

## 8. Raw OCR hierarchy and metadata decision

For every successful non-empty candidate extraction, project-owned models must preserve at least:

* the complete raw recognized text;
* the ordered OCR block list;
* the ordered line list within each block;
* text elements within each line when safely available from ML Kit without changing the version scope;
* block, line, and element text exactly as returned by ML Kit;
* bounding boxes where available;
* corner points where available;
* recognized-language metadata where available;
* confidence metadata using the explicit decision in Section 9; and
* the complete source preprocessing candidate metadata.

`RawOcrGeometry` must preserve a missing bounding box or corner-point set as unavailable rather than failing or inventing geometry. Point and rectangle values must remain coordinate evidence from ML Kit for the candidate image; they are not layout, row, or field coordinates.

The hierarchy must remain raw and ordered. Implementations must not trim, normalize, correct, merge, split, sort by geometry, deduplicate, classify, or otherwise reinterpret OCR text. Empty ML Kit text is represented as an explicit empty extraction outcome retaining the source candidate, with empty raw text and no claimed hierarchy; it is not parsed and does not imply success of any scoreboard field.

## 9. Confidence metadata decision

ML Kit Text Recognition v2 may not expose reliable confidence for all text entities. Project-owned models must represent confidence explicitly as either available or unavailable.

When a reliable confidence value is exposed and can be mapped without interpretation, preserve that value with the entity it belongs to. When ML Kit does not provide a value, the entity type does not support one, or the value cannot be safely represented, return `Unavailable`. Do not derive, estimate, default, normalize, rank, threshold, or fabricate confidence values.

Unavailable confidence is raw extraction metadata only. It must not trigger candidate selection, parsing, manual review UI, team matching, scoring, persistence, or finalization behavior in v0.8.3.

## 10. Failure handling decision

Raw extraction must return typed, project-consistent outcomes rather than crash for unavailable candidate input, OCR engine failure, empty text, incomplete hierarchy, absent geometry, unavailable language, or unavailable confidence.

At minimum, `RawOcrExtractionFailure` must distinguish unavailable input from OCR-engine/extraction failure. An empty OCR response is a controlled explicit empty outcome, not fabricated text and not a parser error. Missing blocks, lines, elements, geometry, language, or confidence do not discard otherwise available raw text; they remain represented as empty or unavailable data.

Coroutine cancellation must remain cancellable. Failures and empty outcomes must retain their source-candidate metadata where the input candidate was accepted. They must not expose screenshot bytes, private paths, URIs, credentials, raw exception text, or unapproved OCR content in logs.

## 11. Persistence and storage decision

The default v0.8.3 decision is in-memory, domain-level preservation of raw extraction results only.

v0.8.3 must not:

* add or modify Room entities, DAOs, migrations, schema exports, or persisted OCR/processing state;
* add Supabase database schema, migrations, Storage objects, upload behavior, synchronization, or OCR metadata records;
* upload OCR text, blocks, lines, elements, geometry, language, confidence, or candidate data to Supabase; or
* alter completed Phase 7 screenshot preservation, metadata, private Storage, duplicate detection, or ownership behavior.

An existing safe raw-OCR persistence path may be used only if one already exists and clearly supports this exact raw-evidence behavior without schema or storage redesign. No such path is required by this decision; in-memory preservation is the approved default.

## 12. Explicit exclusions

v0.8.3 must not:

* parse placements, player names, kills, teams, totals, standings, or any scoreboard semantics;
* perform text normalization, team matching, confidence thresholds, assignment, scoring, finalization, or correction behavior;
* add manual OCR review UI, parsing UI, candidate-selection UI, or workflow integration;
* persist parsed fields, raw OCR fields, confidence metadata, geometry, or processing history by default;
* modify v0.8.0 dependency wiring, recognizer configuration, existing smoke-level recognizer contract, or existing recognizer binding;
* modify v0.8.1 layout definitions or v0.8.2 preprocessing behavior;
* add Room or Supabase schema changes, migrations, Android permissions, screenshots, image fixtures, real player-name fixtures, public sharing, export behavior, cloud OCR, or external AI services; or
* conduct real screenshot evaluation or claim OCR accuracy before v0.8.8.

## 13. Testing and verification expectations

Future v0.8.3 implementation tests must use project-owned fakes or test doubles where possible and verify:

* complete raw recognized text is preserved exactly;
* block and line hierarchy, ordering, and available elements are preserved without parsing;
* available bounding boxes, corner points, and recognized language metadata are retained;
* missing geometry and language are represented safely as unavailable;
* unavailable confidence is explicit and any safely supported available confidence is preserved unchanged;
* source preprocessing-candidate metadata and candidate order are retained;
* empty OCR output becomes a controlled empty outcome;
* engine and unavailable-input failures become typed failures; and
* recognizer resources follow the v0.8.0 lifecycle without mutating caller-owned candidate images.

Tests must not require real screenshot OCR accuracy, the uploaded screenshot, real player names, network access, Google Play Services, Supabase, external files, a parser, or manual review UI. Real screenshot evaluation remains deferred to v0.8.8. Future implementation verification must include focused unit tests and `git diff --check`, plus the relevant Android checks required by the approved implementation task.

## 14. Risks and mitigations

| Risk | Approved mitigation |
| --- | --- |
| Raw OCR is mistaken for confirmed scoreboard data. | Preserve it only through raw project-owned models; prohibit parsing, scoring, matching, review, and finalization in this version. |
| ML Kit geometry or confidence is absent for an entity. | Represent metadata as unavailable and retain available raw text rather than fabricating values or failing extraction. |
| The existing full-text recognizer loses ML Kit hierarchy. | Add only the minimum sibling raw-extraction extension while retaining the v0.8.0 factory, configuration, lifecycle, and existing contract unchanged. |
| Candidate retries become reordered or selected based on content. | Retain v0.8.2 candidate metadata and input order for every extraction outcome; do not select or rank candidates. |
| Raw OCR evidence introduces unapproved privacy or storage exposure. | Keep results in memory by default; do not persist, upload, export, or log private OCR/screenshot content. |

## 15. Acceptance criteria for implementation

v0.8.3 implementation is acceptable only when:

* a project-owned raw extraction contract and ML Kit data implementation exist without leaking ML Kit types into domain models;
* the implementation uses the bundled Latin ML Kit configuration and recognizer lifecycle from v0.8.0 without changing its existing smoke-level contract or binding;
* ordered v0.8.2 preprocessing candidates can be consumed and their complete metadata is retained with each ordered outcome;
* successful extraction preserves full raw text, ordered blocks, ordered lines, safely available elements, and safely available geometry/language metadata without semantic interpretation;
* confidence is represented as explicitly available or unavailable and is never fabricated;
* empty, incomplete, unavailable-input, and engine-failure cases are controlled typed outcomes;
* caller-owned candidate images are not mutated and owned recognizer resources are closed safely;
* focused tests cover raw hierarchy, metadata, confidence, candidate retention, empty output, and failure behavior without real screenshots or OCR-accuracy claims; and
* no parsing, matching, review, scoring, persistence, Room, Supabase, storage, export, permission, or real-evaluation behavior is introduced.

## 16. Next implementation action

After this decision document is reviewed and merged, the next approved implementation task is limited to the raw extraction domain models/interface, the minimal ML Kit raw-hierarchy adapter extension, ordered candidate outcomes, typed failures, and focused tests described here. It must not begin placement parsing, player-name parsing, kill parsing, persistence, review UI, matching, confidence thresholds, scoring, or real screenshot evaluation.
