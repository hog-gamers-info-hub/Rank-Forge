# v0.8.10 — Roster Raw OCR Extraction Decisions

## 1. Status

Approved implementation decision gate for the Phase 8 roster OCR extension, v0.8.10. It follows the completed v0.8.9 Cropped Roster Layout Definition and the closed Phase 7 roster screenshot extension.

This document authorizes only roster raw OCR extraction from approved cropped roster panels. It does not authorize roster parsing, candidate validation, review, confirmation, persistence, synchronization, or any match-result OCR change.

## 2. Version scope

v0.8.10 adds a project-owned, roster-specific raw OCR extraction boundary. It accepts a manually prepared roster-panel crop, validates it against the v0.8.9 cropped roster layout boundary, invokes the existing bundled Latin ML Kit recognizer through an appropriate isolated adapter, and returns raw evidence with roster-region metadata.

The output is in-memory raw OCR evidence only. It is not a team name, player name, validated candidate, roster record, confirmed roster replacement, score, standing, or finalized result.

## 3. Canonical sources reviewed

This decision was reviewed against:

* `AGENTS.md` and `README.md`;
* `docs/project-workflow/00_PHASE_AND_VERSION_ROADMAP.md`;
* `docs/project-workflow/37_ROSTER_SCREENSHOT_OCR_ROADMAP_DECISIONS.md`;
* `docs/project-workflow/41_PHASE_7_ROSTER_SCREENSHOT_EXTENSION_CLOSURE_AUDIT.md`;
* `docs/project-workflow/42_V0_8_9_CROPPED_ROSTER_LAYOUT_DEFINITION_DECISIONS.md`;
* `docs/project-workflow/27_V0_8_0_ML_KIT_INTEGRATION_DECISIONS.md` through `docs/project-workflow/35_V0_8_8_REAL_SCREENSHOT_EVALUATION_DECISIONS.md`;
* the current v0.8.0 ML Kit recognizer/factory, v0.8.2 scoreboard preprocessing, v0.8.3 raw extraction, and v0.8.1 scoreboard layout contracts;
* the current v0.8.9 cropped roster layout and compatibility boundary;
* the current Phase 7 roster screenshot intake, crop preparation, and ordered set-association boundaries; and
* relevant Android, OCR, testing, privacy, security, and AI workflow documents.

## 4. Relationship to v0.8.9 cropped roster layout

v0.8.9 is the only roster-layout authority for this version. v0.8.10 must consume its prepared-crop requirement, roster screenshot positions 1–3, fixed visible-slot order, intended tournament-slot range metadata, slot-content regions, slot-number regions, and four player-row regions.

v0.8.10 must not define, recalibrate, infer, or alter normalized roster regions. It must not reinterpret the v0.8.9 screenshot-position and visible-position mapping as an OCR-to-roster association. The mapping remains metadata only until v0.8.12.

## 5. Cropped-panel input decision

The only supported input is a roster-panel image prepared through the Phase 7 manual in-app crop workflow and accepted by the v0.8.9 cropped-layout compatibility boundary. The extraction request must identify a supported roster screenshot position and retain that prepared-crop context.

Full roster screenshots must not be processed. The extractor must not attempt automatic crop detection, derive full-screen crop coordinates, or accept an uncropped image as though it were an approved panel. Missing cropped input, an unprepared crop, invalid cropped-panel dimensions, an invalid layout, or an unsupported screenshot position must be returned safely as typed outcomes.

## 6. Roster-specific preprocessing decision

The existing v0.8.2 preprocessor is scoreboard-specific: it consumes the fixed scoreboard layout and produces scoreboard-crop candidates. v0.8.10 must not repurpose, broaden, or modify that contract or its Android bitmap implementation for roster panels.

If conversion of the prepared cropped-panel image or its v0.8.9 subregions into recognizer-ready in-memory images is needed, it must be implemented behind a roster-specific preprocessing or image-input boundary. That boundary may crop only the already prepared panel using v0.8.9 region rectangles. It must remain non-destructive, in memory by default, and isolated from scoreboard preprocessing behavior.

This version does not authorize full-screenshot crop detection, new full-screen coordinates, image enhancement policy, persistent processed images, or a broad image-processing refactor.

## 7. ML Kit reuse decision

v0.8.10 may reuse the project-owned bundled ML Kit Latin recognizer factory, configuration, lifecycle, and error/cancellation conventions established in v0.8.0. It must not add another OCR dependency, use an unbundled or Play services model, change `TextRecognizerOptions.DEFAULT_OPTIONS`, add cloud OCR, or place ML Kit calls in UI, ViewModel, Room, Supabase, or parser code.

The existing v0.8.3 raw OCR hierarchy and geometry models may be reused where compatible. Its current candidate/input contract is scoreboard-preprocessing-specific, so v0.8.10 may introduce the minimum roster-specific sibling input, result, and adapter contracts needed to carry cropped-panel and region identity without changing scoreboard extraction behavior.

ML Kit confidence must be preserved only when a reliable value is actually available through the existing raw-evidence mapping. When unavailable, it must be represented explicitly as unavailable. Confidence must never be estimated, defaulted, ranked, thresholded, or invented.

## 8. Raw OCR evidence model decision

The roster raw-evidence model must preserve unmodified recognized text and safely available raw hierarchy, geometry, recognized-language, and confidence metadata. Reuse of `RawOcrBlock`, `RawOcrLine`, `RawOcrElement`, `RawOcrGeometry`, and `RawOcrConfidence` is approved where it does not lose roster context.

Every roster raw-evidence outcome must retain a project-owned metadata envelope containing at least:

* roster screenshot position 1, 2, or 3;
* visible slot position: top-left, top-right, bottom-left, or bottom-right;
* intended tournament-slot range and intended slot metadata only;
* region identity: slot content, slot number, or player row; and
* player-row index 1–4 only when the region identity is player row.

The evidence model must retain missing geometry, language, or confidence as unavailable rather than fabricating values. It must preserve raw extraction order for the requested panel and regions. It must not trim, normalize, correct, merge, split, deduplicate, classify, parse, or otherwise reinterpret recognized text.

## 9. Slot-region extraction decision

Extraction must be bounded by the v0.8.9 cropped roster panel and its explicit slot regions. For each visible slot, the implementation may produce raw extraction outcomes for the slot-content region, its slot-number region, and each of its four player-row regions, retaining the exact region identity for every outcome.

Slot-content extraction remains raw evidence and must not be used to infer a team name, player name, displayed slot number, player count, or candidate correctness. Slot-number and player-row evidence likewise remain raw evidence only. No region may be selected, dropped, or associated with roster data based on its recognized text.

The fixed metadata mapping is: screenshot position plus visible-slot position may state the intended tournament slot, but it must not confirm that any extracted text belongs to that slot. Candidate-to-slot association remains deferred to v0.8.12.

## 10. Failure handling decision

The roster extraction boundary must return typed, project-owned outcomes rather than crash for missing cropped input, unprepared crop, invalid layout, unsupported screenshot position, invalid region conversion, recognizer input failure, recognizer failure, or empty OCR output.

An empty OCR response is an explicit empty raw-evidence outcome for the requested region, not fabricated text and not a parsed-field failure. Missing raw geometry, language, hierarchy members, or confidence must not discard otherwise available raw text. Failures must retain safe request and region metadata when available, but must not expose image bytes, real OCR content in logs, private paths, URIs, credentials, or raw exception text.

Coroutine cancellation must remain cancellable and must not be converted into successful evidence or an unrelated typed success. v0.8.10 may expose typed failure information for later v0.8.13 validation and Phase 9 review, but it must not add a review UI or correction behavior.

## 11. Privacy and fixture decision

Real roster screenshots, real player names, raw OCR payloads, private local paths, signed URLs, and manually verified ground truth must not be committed to the repository without explicit privacy approval.

Automated tests must use synthetic cropped-panel dimensions, synthetic region metadata, and fake OCR engines or results only. Any real-image evaluation is manual, local-only, privacy-controlled, and outside committed fixtures. It must not be required for v0.8.10 unit tests or used to claim OCR quality.

## 12. Explicit exclusions

v0.8.10 must not:

* process full screenshots, guess crop coordinates, alter v0.8.9 layout geometry, or change Phase 7 roster screenshot intake, crop, association, preservation, or restore behavior;
* parse team names or player names; associate OCR text with confirmed roster data; validate roster candidate correctness; normalize text; match teams or players; or calculate confidence decisions;
* add review or correction UI, confirmation behavior, confirmed-roster persistence, Room schema changes, Room migrations, Supabase/backend changes, upload, synchronization, export, or network behavior;
* alter manual roster entry, scoring, standings, match processing, finalized-match protection, correction workflows, or match finalization;
* change the v0.8.0 ML Kit dependency/configuration, v0.8.1 scoreboard layout, v0.8.2 scoreboard preprocessing, v0.8.3 scoreboard raw extraction, or existing match-result OCR behavior; or
* add real screenshots, image files, real player-name fixtures, cloud OCR, external AI services, or a broad OCR/image refactor.

## 13. Testing and verification expectations

Future v0.8.10 implementation tests must use pure domain models, fakes, or test doubles and verify:

* roster raw extraction result models preserve text, safely available raw hierarchy/geometry, and explicit unavailable confidence;
* screenshot position, visible-slot position, intended-slot metadata, and region identity are preserved for every outcome;
* slot-content, slot-number, and player-row region identity are distinguishable, and player-row indices are limited to 1–4 where applicable;
* prepared cropped-panel input and v0.8.9 layout compatibility are required before extraction;
* missing cropped input, invalid layout, unsupported screenshot position, invalid region, empty OCR output, recognizer failure, and cancellation are handled safely according to the typed boundary;
* fake recognizer output does not become parsed text or roster data; and
* existing scoreboard layout and raw-extraction contracts remain unchanged.

Tests must not require ML Kit runtime execution, OCR accuracy, real screenshots, real names, network access, Google Play services, Supabase, Room migrations, or external files. Manual device or instrumentation verification may be required later if an approved implementation touches actual ML Kit execution.

## 14. Risks and mitigations

| Risk | Approved mitigation |
| --- | --- |
| Full-screen game UI contaminates roster OCR. | Require a Phase 7 prepared crop and reject unprepared/full-screen input without crop guessing. |
| Scoreboard preprocessing or raw extraction is unintentionally changed for roster work. | Use roster-specific sibling contracts where the existing scoreboard contracts are shape-specific; leave the completed scoreboard path unchanged. |
| Raw region text is mistaken for parsed or confirmed roster data. | Preserve raw text with explicit region metadata only; defer parsing, association, validation, review, and confirmation. |
| Missing ML Kit confidence is treated as a quality score. | Preserve only reliably available values and represent all other confidence as unavailable. |
| OCR failures lose context or crash processing. | Return typed outcomes with safe request/region metadata and propagate cancellation. |
| Private images, names, or raw OCR data enter source control. | Require synthetic automated fixtures and keep real evaluation local-only under privacy controls. |

## 15. Acceptance criteria for implementation

v0.8.10 implementation is acceptable only when:

* a roster-specific raw OCR extraction boundary consumes only prepared cropped roster panels validated by the v0.8.9 layout boundary;
* it reuses the existing bundled Latin ML Kit boundary and compatible raw models without modifying completed scoreboard OCR contracts;
* every raw outcome retains screenshot position, visible-slot position, intended-slot metadata, region identity, and player-row index when applicable;
* slot-content, slot-number, and exactly four supported player-row regions can be represented without semantic interpretation;
* raw text, hierarchy, geometry, language, and confidence are preserved only as supplied, with unavailable metadata explicit and no fabricated confidence;
* empty and failure outcomes are typed, safe, and cancellation remains cancellable;
* focused synthetic tests cover model, metadata, region, input, empty, and failure behavior plus scoreboard regression protection; and
* no parsing, validation, review UI, confirmation, persistence, Room, Supabase, upload, sync, scoring, standings, finalization, real fixture, or full-screenshot processing behavior is introduced.

## 16. Next implementation action

After this decision document is reviewed and merged, and with explicit user approval, the next v0.8.10 implementation task is limited to roster-specific raw extraction models/interfaces, a minimal data adapter that reuses the existing bundled ML Kit boundary where appropriate, isolated in-memory roster-region input preparation if required, typed outcomes, and focused synthetic tests.

v0.8.11 will parse candidate team and player names later. v0.8.12 will map parsed OCR candidates to tournament slots later. v0.8.13 will validate roster OCR candidates later. Phase 9 will provide review and correction before confirmation. v0.5.8 and v0.6.9 remain responsible for confirmed-roster persistence and sync safety. Five- and six-player visible-row extraction remains unsupported pending approved representative evidence, and real roster OCR-quality evaluation remains deferred to Phase 12 unless separately approved.
