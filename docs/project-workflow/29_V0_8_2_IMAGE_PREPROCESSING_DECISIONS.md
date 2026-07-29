# v0.8.2 — Image Preprocessing Decisions

## 1. Status

Approved implementation decision gate for Phase 8 v0.8.2. Phase 7 is complete and closed, and v0.8.0 ML Kit Integration and v0.8.1 Fixed Scoreboard Layout Definition are complete and merged.

This document authorizes only image preprocessing for OCR readiness after review and merge. It does not authorize OCR extraction, raw-text persistence, parsing, review UI, result processing, or any work assigned to later roadmap versions.

## 2. Version scope

v0.8.2 adds deterministic image preparation for a supported scoreboard image before a later approved OCR-extraction step. Its scope is limited to:

* validation of a preprocessing input and its dimensions;
* use of the v0.8.1 fixed layout to select approved crop regions;
* non-destructive cropping, scaling, contrast adjustment, and ordered enhancement retry variants;
* project-owned preprocessing input, candidate, step, and failure abstractions; and
* an Android/data implementation that creates OCR-ready in-memory candidates.

The output is an OCR-ready image candidate or ordered candidate set only. It is not OCR text, a parsed result, a confirmed match result, or a persisted processing record.

## 3. Canonical sources reviewed

This decision was reviewed against:

* `AGENTS.md` and `README.md`;
* `docs/project-workflow/00_PHASE_AND_VERSION_ROADMAP.md`;
* `docs/project-workflow/26_PHASE_7_CLOSURE_AUDIT.md`;
* `docs/project-workflow/27_V0_8_0_ML_KIT_INTEGRATION_DECISIONS.md` and `docs/project-workflow/28_V0_8_1_FIXED_SCOREBOARD_LAYOUT_DECISIONS.md`;
* `docs/project-workflow/19_V0_7_0_PHOTO_PICKER_INTEGRATION_DECISIONS.md` through `docs/project-workflow/25_V0_7_6_SCREENSHOT_METADATA_DECISIONS.md`;
* `docs/02_SYSTEM_ARCHITECTURE.md`, `docs/04_OCR_AND_TEAM_MATCHING.md`, and `docs/06_ANDROID_APP.md`;
* `docs/09_TESTING_AND_ACCEPTANCE.md` and `docs/11_SECURITY_AND_PRIVACY.md`;
* `docs/ai/00_AI_WORKFLOW.md`; and
* the current OCR recognizer, fixed-layout, image-validation, and local-image-preservation implementations and tests.

The roadmap assigns cropping, scaling, contrast adjustment, and enhancement retries to v0.8.2. Phase 7 remains the authoritative boundary for original screenshot ownership, metadata, private storage, and upload behavior.

## 4. Relationship to v0.8.0 ML Kit integration

v0.8.0 provides the isolated bundled-Latin ML Kit recognizer boundary. v0.8.2 must not change its dependency, recognizer configuration, factory, adapter, text result model, or failure mapping.

Preprocessing produces a project-owned OCR-ready candidate that a later approved extraction workflow may supply to that recognizer boundary. v0.8.2 must not invoke ML Kit as an accuracy workflow, inspect recognized text, or use OCR output to choose, parse, score, or confirm a candidate.

## 5. Relationship to v0.8.1 fixed layout

v0.8.2 must consume the current v0.8.1 fixed layout definitions rather than duplicate or redefine coordinates. In particular, preprocessing must use the approved `ScoreboardLayoutValidator`, normalized overall scoreboard-content rectangle, panel definitions, and exclusion-zone precedence.

Input is eligible only when dimensions are positive, the image is landscape, and its aspect ratio is within the v0.8.1 supported range. Unsupported layout validation is a typed preprocessing failure, not a parsing or manual-review outcome.

The first crop is the overall scoreboard-content rectangle. Panel-specific crops may be represented as later candidates using the existing left and right panel definitions, but they are preparation artifacts only: they must not detect or parse any row, placement, player, elimination, or team value.

## 6. Preprocessing boundary decision

Preprocessing must be isolated behind project-owned abstractions. The approved default package boundary is:

* domain models and interface under `app/src/main/java/com/hoggamers/rankforge/domain/ocr/preprocessing/`; and
* Android bitmap/image implementation under `app/src/main/java/com/hoggamers/rankforge/data/ocr/preprocessing/`.

The implementation may use the following focused names, adjusted only to match established project naming conventions:

* `OcrImagePreprocessor`;
* `OcrPreprocessingInput`;
* `OcrPreprocessingCandidate`;
* `OcrPreprocessingStep`;
* `OcrPreprocessingFailure`; and
* `AndroidBitmapOcrImagePreprocessor`.

The domain-facing candidate must identify its ordered position, the layout crop it represents, and the ordered preprocessing steps applied. It may carry or reference the in-memory OCR-ready image through a platform-safe boundary. The abstraction must allow later approved extraction to consume a candidate without replacing the ML Kit integration.

Preprocessing must not be implemented directly in a composable, ViewModel, Room component, Supabase component, screenshot-storage component, or ML Kit adapter class. Coordination with any future UI or OCR caller must remain outside the bitmap transformation implementation.

## 7. Approved preprocessing pipeline

The approved v0.8.2 pipeline is deterministic and limited to OCR readiness:

1. **Input validation.** Reject non-positive dimensions. Use the v0.8.1 layout validator to reject portrait or unsupported-aspect-ratio inputs. Fail safely when an image cannot be decoded or read.
2. **Crop.** Convert the v0.8.1 normalized overall scoreboard-content rectangle to the current image dimensions and crop that region first. The implementation must verify the converted crop is within bounds and has positive pixel dimensions before creating a bitmap. Panel-specific candidates may use the existing panel rectangles after the overall crop decision, but must not introduce coordinate data or parsing.
3. **Scale.** Support bounded, deterministic upscaling for OCR readability. The approved baseline factors are `1.5x` and `2.0x` when the projected dimensions and pixel allocation remain within an implementation-defined safe bound. The implementation must reject or omit a variant that would exceed that bound; it must not allocate uncontrolled large bitmaps.
4. **Contrast.** Support deterministic contrast enhancement suited to dark or light scoreboard text. Enhancement must create a separate result and must not destructively mutate the source image or the baseline crop.
5. **Retry variants.** Return an ordered set of candidates. The baseline overall-scoreboard crop is first. Scaled and contrast-enhanced retry variants follow in a documented deterministic order, with metadata that records every applied preprocessing step. A later version may decide which candidate to submit to OCR; v0.8.2 does not use text output to alter the order.

No rotation, compression, denoising, thresholding, layout inference, field extraction, or other image-processing behavior is approved unless it is strictly required to perform the crop, bounded scale, and deterministic contrast steps above.

## 8. Candidate and retry strategy

The candidate sequence must be stable for the same valid input and layout. The required ordering is:

1. baseline overall-scoreboard crop;
2. approved scaled baseline variant or variants; and
3. approved contrast-enhanced retry variant or variants after their corresponding baseline/scaled candidate.

Each candidate must expose metadata sufficient to state whether it is the overall or a permitted panel crop and which of `CROP`, `SCALE`, and `CONTRAST` were applied. It must not contain recognized text, raw ML Kit blocks or lines, parsed values, confidence, team candidates, or review status.

The initial implementation may limit candidates to the overall-scoreboard crop and its bounded variants. If panel candidates are included, they must use only the v0.8.1 panel definitions and follow the same deterministic ordering and metadata rules.

## 9. Image ownership and resource handling

The original screenshot is evidence owned by the completed Phase 7 preservation and storage workflow. v0.8.2 must preserve it unchanged, including its original bytes and metadata behavior.

The default v0.8.2 decision is that processed OCR candidates are in-memory only. The implementation must not recycle, close, or mutate a caller-owned bitmap or image unexpectedly. It may release or recycle only intermediate resources that it created and exclusively owns, and only after they are no longer used by a returned candidate. Decode streams and other owned closeable resources must be closed safely, and processing must avoid memory leaks.

An existing Phase 7 local-preservation abstraction may be used only for safe temporary or intermediate handling if its current behavior clearly supports that use without a schema, storage, lifecycle, or ownership redesign. The current default remains in-memory candidates; no temporary-file behavior is required or implied by this decision.

## 10. Failure handling decision

The preprocessing boundary must return typed, project-consistent failures rather than crashing for invalid dimensions, unsupported layout, unreadable or failed image decode, invalid or out-of-bounds crop conversion, and bitmap-allocation or transformation failure.

Failure categories may distinguish at least invalid input dimensions, unsupported layout, unreadable input, invalid crop, and resource/allocation failure. They must not expose image bytes, private screenshot contents, raw paths, URIs, credentials, or raw exception text. Coroutine cancellation must remain cancellable rather than being converted into a successful candidate or an unrelated failure.

A failure must not modify the original screenshot, screenshot metadata, local-preservation state, Supabase object, match state, finalized protection, scoring, standings, or correction state.

## 11. Persistence and storage decision

Processed candidates are in-memory only by default in v0.8.2.

v0.8.2 must not:

* persist processed images or preprocessing candidates unless an existing Phase 7 safe temporary/intermediate path clearly fits without redesign;
* add Room entities, columns, DAOs, migrations, schema exports, or persisted preprocessing/OCR state;
* add Supabase database schema, migration, Storage-object, upload, synchronization, or metadata changes; or
* upload processed candidates to Supabase Storage.

Existing original screenshot preservation, private Storage upload, screenshot metadata, duplicate detection, and app-private path behavior remain unchanged. This version adds no Android storage, media, or camera permission.

## 12. Explicit exclusions

v0.8.2 must not:

* run OCR as an accuracy workflow or conduct real screenshot evaluation; real screenshot evaluation remains deferred to v0.8.8;
* parse placements, player names, kills, teams, totals, standings, confidence, or any other scoreboard field;
* persist OCR blocks, lines, raw text, parsed fields, confidence metadata, preprocessing history, or review state;
* add manual OCR review UI, extraction UI, team matching, correction behavior, or finalized-result workflow changes;
* modify ML Kit dependency wiring or the existing recognizer adapter;
* modify finalized-match protection, scoring, standings, correction workflows, Room persistence, Supabase synchronization, screenshot metadata, or screenshot-storage behavior;
* add Room or Supabase schema changes, migrations, permissions, screenshot fixtures, image files, cloud OCR, or external AI services; or
* define another Phase 8 version, a second scoreboard layout, new crop coordinates, or work assigned to v0.8.3 and later.

## 13. Testing and verification expectations

Future v0.8.2 implementation tests must use pure models, fakes, or test doubles where possible and verify:

* invalid dimensions return a validation failure;
* portrait and unsupported-aspect-ratio inputs are rejected through the v0.8.1 layout-validation boundary;
* normalized crop conversion consumes the v0.8.1 layout rectangles;
* candidate ordering is deterministic, with the baseline overall crop first;
* baseline, scaled, contrast-adjusted, and retry candidates expose their applied-step metadata;
* original input is not mutated where testable;
* invalid crop, decode, and bitmap/resource failures are returned rather than thrown where possible; and
* caller-owned image resources are not unexpectedly released.

Tests must not require OCR accuracy, a real uploaded screenshot, real player names, network access, Google Play services, Supabase, external files, or a manual OCR review surface. Real screenshot evaluation remains deferred to v0.8.8. Future implementation verification must include focused unit tests and `git diff --check`, plus the relevant Android checks required by the approved implementation task.

## 14. Risks and mitigations

| Risk | Approved mitigation |
| --- | --- |
| Large source images or aggressive scaling exhaust bitmap memory. | Validate dimensions, enforce a bounded allocation limit, and omit or fail oversized variants safely. |
| A transformation changes or replaces original evidence. | Treat the Phase 7 original as caller-owned evidence; create separate in-memory candidates and never destructively mutate it. |
| Layout behavior drifts from v0.8.1. | Use the existing normalized rectangles and layout validator; do not duplicate or redefine coordinates. |
| Candidate retries become non-repeatable or imply OCR accuracy. | Define stable candidate ordering and step metadata; do not invoke OCR or score candidate accuracy in this version. |
| Processed variants create unapproved storage or privacy exposure. | Keep candidates in memory by default; do not persist, upload, log, or add metadata for them. |

## 15. Acceptance criteria for implementation

v0.8.2 implementation is acceptable only when:

* preprocessing is available through a project-owned domain abstraction with an Android/data bitmap implementation;
* no UI, ViewModel, Room, Supabase, screenshot-storage, or ML Kit adapter class directly owns preprocessing behavior;
* input validation rejects non-positive dimensions, unreadable input, and v0.8.1-incompatible layout safely;
* overall-scoreboard cropping uses the v0.8.1 normalized layout definition;
* bounded deterministic `1.5x` and `2.0x` scale support and non-destructive deterministic contrast support are available where safe;
* the baseline crop is first and each returned candidate records its ordered preprocessing steps;
* original screenshots remain unchanged and processed candidates remain in memory by default;
* typed failures cover invalid dimensions, layout, decode/input, crop, and bitmap/resource conditions without crashing;
* focused tests cover the required validation, layout, candidate, ordering, ownership, and failure behavior without real screenshot accuracy; and
* no excluded OCR extraction, parsing, persistence, UI, Room, Supabase, storage, permission, scoring, standings, correction, or finalization behavior is introduced.

## 16. Next implementation action

After this decision document is reviewed and merged, the next approved implementation task is limited to the project-owned preprocessing models/interface, Android bitmap implementation, deterministic crop/scale/contrast candidate generation, typed failures, and focused tests described here. It must not begin raw OCR text extraction, persistence, parsing, review UI, matching, or real screenshot evaluation.
